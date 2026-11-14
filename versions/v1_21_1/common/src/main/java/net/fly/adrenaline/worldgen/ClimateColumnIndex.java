package net.fly.adrenaline.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.level.biome.Climate;

public final class ClimateColumnIndex {

    private static final long MAX_PARAMETER = 1_000_000L;
    private static final int COLUMN_COUNT = 16;
    private static final int MAX_DEPTH_GROUPS = 8;

    private final Node root;
    private final Node[] nodes;
    private final IdentityHashMap<Object, Node> leaves;
    private final ThreadLocal<Object> lastResult;
    private final DepthGroup[] depthGroups;
    private final ThreadLocal<Columns> columns = ThreadLocal.withInitial(Columns::new);

    private ClimateColumnIndex(Node root, Node[] nodes, IdentityHashMap<Object, Node> leaves, ThreadLocal<Object> lastResult) {
        this.root = root;
        this.nodes = nodes;
        this.leaves = leaves;
        this.lastResult = lastResult;
        this.depthGroups = depthGroups(root, nodes);
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
        Column column = this.columns.get().get(target, this.depthGroups == null ? this.nodes.length : 0);
        long depth = target.depth();
        Node incumbent = this.leaves.get(this.lastResult.get());
        Node winner;
        if (this.depthGroups == null) {
            winner = search(this.root, incumbent, column, depth);
        } else {
            column.prepareGroups(this.depthGroups, incumbent);
            winner = searchGroups(column, incumbent, depth);
        }
        this.lastResult.set(winner.original);
        return winner.value;
    }

    private Node searchGroups(Column column, Node incumbent, long depth) {
        Node winner = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < this.depthGroups.length; i++) {
            DepthGroup group = this.depthGroups[i];
            Node candidate = column.groupWinners[i];
            long delta = delta(depth, group.min, group.max);
            long distance = column.groupDistances[i] + delta * delta;
            if (distance < bestDistance || distance == bestDistance && candidate.index < winner.index) {
                winner = candidate;
                bestDistance = distance;
            }
        }
        if (incumbent != null && incumbent != winner) {
            long delta = delta(depth, incumbent.minDepth, incumbent.maxDepth);
            if (baseDistance(incumbent, column.coordinates) + delta * delta == bestDistance) {
                return incumbent;
            }
        }
        return winner;
    }

    private static Node searchBase(Node node, Node incumbent, long[] coordinates) {
        if (node.children == null) {
            return node;
        }
        Node winner = incumbent;
        long bestDistance = incumbent == null ? Long.MAX_VALUE : baseDistance(incumbent, coordinates);
        for (Node child : node.children) {
            long lowerBound = baseDistance(child, coordinates);
            if (lowerBound < bestDistance || lowerBound == bestDistance && child.index < winner.index) {
                Node candidate = searchBase(child, winner, coordinates);
                long distance = child == candidate ? lowerBound : baseDistance(candidate, coordinates);
                if (distance < bestDistance || distance == bestDistance && candidate.index < winner.index) {
                    bestDistance = distance;
                    winner = candidate;
                }
            }
        }
        return winner;
    }

    private static long baseDistance(Node node, long[] coordinates) {
        long distance = 0L;
        for (int i = 0; i < 7; i++) {
            if (i != 4) {
                long delta = node.parameters[i].distance(coordinates[i]);
                distance += delta * delta;
            }
        }
        return distance;
    }

    private static DepthGroup[] depthGroups(Node root, Node[] nodes) {
        LinkedHashMap<DepthRange, Node> ranges = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node.children == null) {
                ranges.putIfAbsent(new DepthRange(node.minDepth, node.maxDepth), node);
                if (ranges.size() > MAX_DEPTH_GROUPS) {
                    return null;
                }
            }
        }
        DepthGroup[] groups = new DepthGroup[ranges.size()];
        int next = 0;
        for (DepthRange range : ranges.keySet()) {
            groups[next++] = new DepthGroup(filterDepth(root, range), range.min, range.max);
        }
        return groups;
    }

    private static Node filterDepth(Node node, DepthRange range) {
        if (node.children == null) {
            return node.minDepth == range.min && node.maxDepth == range.max ? node : null;
        }
        List<Node> children = new ArrayList<>();
        for (Node child : node.children) {
            Node filtered = filterDepth(child, range);
            if (filtered != null) {
                children.add(filtered);
            }
        }
        if (children.isEmpty()) {
            return null;
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        Climate.Parameter[] parameters = new Climate.Parameter[7];
        for (int i = 0; i < parameters.length; i++) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (Node child : children) {
                min = Math.min(min, child.parameters[i].min());
                max = Math.max(max, child.parameters[i].max());
            }
            parameters[i] = new Climate.Parameter(min, max);
        }
        Node filtered = new Node(node.index, node.original, parameters);
        filtered.children = children.toArray(Node[]::new);
        return filtered;
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

    private record DepthRange(long min, long max) {
    }

    private record DepthGroup(Node root, long min, long max) {
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
        private Node[] groupWinners;
        private long[] groupDistances;
        private boolean groupsReady;

        private void prepare(int nodeCount) {
            if (nodeCount != 0 && this.distances == null) {
                this.distances = new long[nodeCount];
                this.generations = new int[nodeCount];
            }
        }

        private void reset(Climate.TargetPoint target) {
            this.groupsReady = false;
            this.coordinates[0] = target.temperature();
            this.coordinates[1] = target.humidity();
            this.coordinates[2] = target.continentalness();
            this.coordinates[3] = target.erosion();
            this.coordinates[5] = target.weirdness();
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
            long distance = baseDistance(node, this.coordinates);
            if (this.distances != null) {
                this.distances[node.index] = distance;
                this.generations[node.index] = this.generation;
            }
            return distance;
        }

        private void prepareGroups(DepthGroup[] groups, Node incumbent) {
            if (this.groupsReady) {
                return;
            }
            if (this.groupWinners == null) {
                this.groupWinners = new Node[groups.length];
                this.groupDistances = new long[groups.length];
            }
            for (int i = 0; i < groups.length; i++) {
                DepthGroup group = groups[i];
                Node seed = this.groupWinners[i];
                if (seed == null && incumbent != null && incumbent.minDepth == group.min && incumbent.maxDepth == group.max) {
                    seed = incumbent;
                }
                Node winner = searchBase(group.root, seed, this.coordinates);
                this.groupWinners[i] = winner;
                this.groupDistances[i] = baseDistance(winner, this.coordinates);
            }
            this.groupsReady = true;
        }
    }
}
