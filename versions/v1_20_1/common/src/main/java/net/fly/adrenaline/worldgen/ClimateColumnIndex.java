package net.fly.adrenaline.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.world.level.biome.Climate;

public final class ClimateColumnIndex {

    private static final long MAX_PARAMETER = 1_000_000L;
    private static final int COLUMN_COUNT = 16;
    private static final long CERTIFICATE_RADIUS = 2048L;

    private final Node root;
    private final Node[] nodes;
    private final IdentityHashMap<Object, Node> leaves;
    private final ThreadLocal<Object> lastResult;
    private final ThreadLocal<Columns> columns = ThreadLocal.withInitial(Columns::new);

    private ClimateColumnIndex(Node root, Node[] nodes, IdentityHashMap<Object, Node> leaves, ThreadLocal<Object> lastResult) {
        this.root = root;
        this.nodes = nodes;
        this.leaves = leaves;
        this.lastResult = lastResult;
    }

    @SuppressWarnings("unchecked")
    public static ClimateColumnIndex create(Object parameterList) {
        if (parameterList.getClass() != Climate.ParameterList.class) {
            return null;
        }
        try {
            Object tree = null;
            for (Field field : parameterList.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() != List.class) {
                    field.setAccessible(true);
                    Object candidate = field.get(parameterList);
                    if (candidate != null && threadLocalField(candidate.getClass()) != null) {
                        tree = candidate;
                        break;
                    }
                }
            }
            if (tree == null) {
                return null;
            }
            Field lastResultField = threadLocalField(tree.getClass());
            lastResultField.setAccessible(true);
            ThreadLocal<Object> lastResult = (ThreadLocal<Object>) lastResultField.get(tree);
            for (Field field : tree.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() == ThreadLocal.class) {
                    continue;
                }
                field.setAccessible(true);
                Object candidate = field.get(tree);
                if (candidate != null && parameterField(candidate.getClass()) != null) {
                    List<Node> nodes = new ArrayList<>();
                    IdentityHashMap<Object, Node> leaves = new IdentityHashMap<>();
                    Node root = build(candidate, nodes, leaves);
                    return new ClimateColumnIndex(root, nodes.toArray(Node[]::new), leaves, lastResult);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return null;
    }

    public boolean supports(Climate.TargetPoint target) {
        return bounded(target.temperature()) && bounded(target.humidity()) && bounded(target.continentalness())
            && bounded(target.erosion()) && bounded(target.depth()) && bounded(target.weirdness());
    }

    public Object search(Climate.TargetPoint target) {
        Column column = this.columns.get().get(target, this.nodes.length);
        long depth = target.depth();
        Node winner;
        if (column.certified != null && depth >= column.certifiedMin && depth <= column.certifiedMax) {
            winner = column.certified;
        } else {
            winner = search(this.root, this.leaves.get(this.lastResult.get()), column, depth);
            if (++column.queries >= column.nextCertificate && winner == column.previous) {
                column.nextCertificate = column.queries + 8;
                long min = depth - CERTIFICATE_RADIUS;
                long max = depth + CERTIFICATE_RADIUS;
                if (certify(this.root, winner, column, min, max)) {
                    column.certified = winner;
                    column.certifiedMin = min;
                    column.certifiedMax = max;
                }
            }
            column.previous = winner;
        }
        this.lastResult.set(winner.original);
        return winner.value;
    }

    private static Node search(Node node, Node incumbent, Column column, long depth) {
        if (node.children == null) {
            return node;
        }
        long bestDistance = incumbent == null ? Long.MAX_VALUE : distance(incumbent, column, depth);
        Node best = incumbent;
        for (Node child : node.children) {
            long lowerBound = distance(child, column, depth);
            if (bestDistance > lowerBound) {
                Node candidate = search(child, best, column, depth);
                long candidateDistance = child == candidate ? lowerBound : distance(candidate, column, depth);
                if (bestDistance > candidateDistance) {
                    bestDistance = candidateDistance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static long distance(Node node, Column column, long depth) {
        long delta = delta(depth, node.minDepth, node.maxDepth);
        return column.base(node) + delta * delta;
    }

    private static boolean certify(Node node, Node winner, Column column, long min, long max) {
        if (node == winner) {
            return true;
        }
        if (strictlyFarther(node, winner, column, min, max)) {
            return true;
        }
        if (node.children == null) {
            return false;
        }
        for (Node child : node.children) {
            if (!certify(child, winner, column, min, max)) {
                return false;
            }
        }
        return true;
    }

    private static boolean strictlyFarther(Node node, Node winner, Column column, long min, long max) {
        return fartherAt(node, winner, column, min) && fartherAt(node, winner, column, max)
            && fartherAt(node, winner, column, clamp(node.minDepth, min, max))
            && fartherAt(node, winner, column, clamp(node.maxDepth, min, max))
            && fartherAt(node, winner, column, clamp(winner.minDepth, min, max))
            && fartherAt(node, winner, column, clamp(winner.maxDepth, min, max));
    }

    private static boolean fartherAt(Node node, Node winner, Column column, long depth) {
        return distance(node, column, depth) > distance(winner, column, depth);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long delta(long value, long min, long max) {
        return value > max ? value - max : Math.max(min - value, 0L);
    }

    private static boolean bounded(long value) {
        return value >= -MAX_PARAMETER && value <= MAX_PARAMETER;
    }

    private static Node build(Object original, List<Node> nodes, IdentityHashMap<Object, Node> leaves) throws ReflectiveOperationException {
        Field parametersField = parameterField(original.getClass());
        parametersField.setAccessible(true);
        Climate.Parameter[] parameters = (Climate.Parameter[]) parametersField.get(original);
        if (parameters.length != 7) {
            throw new IllegalArgumentException("Climate dimensions");
        }
        for (Climate.Parameter parameter : parameters) {
            if (!bounded(parameter.min()) || !bounded(parameter.max())) {
                throw new IllegalArgumentException("Climate parameter range");
            }
        }
        Node node = new Node(nodes.size(), original, parameters);
        nodes.add(node);
        for (Field field : original.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            if (field.getType().isArray() && field.getType() != Climate.Parameter[].class) {
                Object[] children = (Object[]) field.get(original);
                node.children = new Node[children.length];
                for (int i = 0; i < children.length; i++) {
                    node.children[i] = build(children[i], nodes, leaves);
                }
            } else if (field.getType() == Object.class) {
                node.value = field.get(original);
            }
        }
        if (node.children == null) {
            leaves.put(original, node);
        }
        return node;
    }

    private static Field parameterField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == Climate.Parameter[].class) {
                    return field;
                }
            }
        }
        return null;
    }

    private static Field threadLocalField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == ThreadLocal.class) {
                return field;
            }
        }
        return null;
    }

    private static final class Node {
        private final int index;
        private final Object original;
        private final Climate.Parameter[] parameters;
        private final long minDepth;
        private final long maxDepth;
        private Node[] children;
        private Object value;

        private Node(int index, Object original, Climate.Parameter[] parameters) {
            this.index = index;
            this.original = original;
            this.parameters = parameters;
            this.minDepth = parameters[4].min();
            this.maxDepth = parameters[4].max();
        }
    }

    private static final class Columns {
        private final Column[] entries = new Column[COLUMN_COUNT];
        private int next;

        private Column get(Climate.TargetPoint target, int nodeCount) {
            for (Column column : this.entries) {
                if (column != null && column.matches(target)) {
                    column.prepare(nodeCount);
                    return column;
                }
            }
            Column column = this.entries[this.next];
            if (column == null) {
                column = new Column();
                this.entries[this.next] = column;
            }
            column.reset(target);
            this.next = (this.next + 1) % COLUMN_COUNT;
            return column;
        }
    }

    private static final class Column {
        private final long[] coordinates = new long[7];
        private long[] distances;
        private int[] generations;
        private int generation;
        private Node previous;
        private Node certified;
        private long certifiedMin;
        private long certifiedMax;
        private int queries;
        private int nextCertificate = 2;

        private void prepare(int nodeCount) {
            if (this.distances == null) {
                this.distances = new long[nodeCount];
                this.generations = new int[nodeCount];
            }
        }

        private void reset(Climate.TargetPoint target) {
            this.coordinates[0] = target.temperature();
            this.coordinates[1] = target.humidity();
            this.coordinates[2] = target.continentalness();
            this.coordinates[3] = target.erosion();
            this.coordinates[5] = target.weirdness();
            this.previous = null;
            this.certified = null;
            this.queries = 0;
            this.nextCertificate = 2;
            if (this.generation == Integer.MAX_VALUE) {
                if (this.generations != null) {
                    Arrays.fill(this.generations, 0);
                }
                this.generation = 1;
            } else {
                this.generation++;
            }
        }

        private boolean matches(Climate.TargetPoint target) {
            return this.coordinates[0] == target.temperature() && this.coordinates[1] == target.humidity()
                && this.coordinates[2] == target.continentalness() && this.coordinates[3] == target.erosion()
                && this.coordinates[5] == target.weirdness();
        }

        private long base(Node node) {
            if (this.distances != null && this.generations[node.index] == this.generation) {
                return this.distances[node.index];
            }
            long distance = 0L;
            for (int i = 0; i < 7; i++) {
                if (i != 4) {
                    long delta = node.parameters[i].distance(this.coordinates[i]);
                    distance += delta * delta;
                }
            }
            if (this.distances != null) {
                this.distances[node.index] = distance;
                this.generations[node.index] = this.generation;
            }
            return distance;
        }
    }
}
