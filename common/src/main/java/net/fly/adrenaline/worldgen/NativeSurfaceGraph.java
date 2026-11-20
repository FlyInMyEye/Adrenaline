package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NativeSurfaceGraph {

    static final int NODE_LIMIT = 65536;
    static final int NODE_BYTES = 32;
    private static final int INDEX_LIMIT = 262144;

    private NativeSurfaceGraph() {
    }

    static Code compact(List<Node> nodes, Tables tables, int[] entries) {
        var compactor = new GraphCompactor(nodes, tables.indices);
        int[] roots;
        try {
            roots = compactor.compact(entries);
        } catch (IllegalArgumentException ignored) {
            return new Code(nodes, tables, entries);
        }
        if (compactor.nodes.size() > NODE_LIMIT
            || byteCount(compactor.nodes, compactor.tables) >= byteCount(nodes, tables)) {
            return new Code(nodes, tables, entries);
        }
        return new Code(compactor.nodes, compactor.tables, roots);
    }

    private static int byteCount(List<Node> nodes, Tables tables) {
        return nodes.size() * NODE_BYTES + tables.size() * Integer.BYTES;
    }

    record Code(List<Node> nodes, Tables tables, int[] entries) { }

    record Node(int op, int a, int b, int c, int d, int yes, int no) { }

    static final class Tables {

        private final List<Integer> indices = new ArrayList<>();
        private final Map<List<Integer>, Integer> offsets = new HashMap<>();

        int add(List<Integer> values) {
            Integer existing = this.offsets.get(values);
            if (existing != null) {
                return existing;
            }
            if (this.indices.size() + values.size() > INDEX_LIMIT) {
                throw new IllegalArgumentException("Surface lookup table size");
            }
            int offset = this.indices.size();
            List<Integer> copy = List.copyOf(values);
            this.offsets.put(copy, offset);
            this.indices.addAll(copy);
            return offset;
        }

        int size() {
            return this.indices.size();
        }

        void write(ByteBuffer buffer) {
            for (int index : this.indices) {
                buffer.putInt(index);
            }
        }
    }

    private static final class GraphCompactor {

        private static final int BIOME_PENDING = 1;
        private static final int BIOME_USED = 2;

        private final List<Node> original;
        private final List<Integer> indices;
        private final List<Node> nodes = new ArrayList<>();
        private final Tables tables = new Tables();
        private final Map<Node, Integer> intern = new HashMap<>();

        private GraphCompactor(List<Node> original, List<Integer> indices) {
            this.original = original;
            this.indices = indices;
        }

        private int[] compact(int[] entries) {
            int size = this.original.size();
            byte[] states = new byte[size];
            for (int entry : entries) {
                states[entry] = BIOME_PENDING;
            }
            for (int i = size - 1; i >= 0; i--) {
                if (states[i] == 0) {
                    continue;
                }
                Node node = this.original.get(i);
                switch (node.op) {
                    case 0, 12 -> { }
                    case 7 -> states[node.yes] |= BIOME_USED;
                    case 14 -> {
                        for (int j = 0; j < 256; j++) {
                            states[this.indices.get(node.a + j)] |= states[i];
                        }
                    }
                    default -> {
                        states[node.yes] |= states[i];
                        states[node.no] |= states[i];
                    }
                }
            }

            int[] pending = new int[size];
            int[] used = new int[size];
            for (int i = 0; i < size; i++) {
                Node node = this.original.get(i);
                if ((states[i] & BIOME_USED) != 0) {
                    used[i] = node.op == 7 ? used[node.yes] : this.remap(node, used);
                }
                if ((states[i] & BIOME_PENDING) != 0) {
                    pending[i] = node.op == 7 ? this.remap(node, used) : this.remap(node, pending);
                }
            }
            int[] compacted = new int[entries.length];
            for (int i = 0; i < entries.length; i++) {
                compacted[i] = pending[entries[i]];
            }
            return compacted;
        }

        private int remap(Node node, int[] successors) {
            boolean localCondition = node.op == 1 && node.c == 0 || node.op >= 2 && node.op <= 4;
            if (localCondition && successors[node.yes] == successors[node.no]) {
                return successors[node.yes];
            }
            Node mapped = switch (node.op) {
                case 0 -> node;
                case 7 -> new Node(7, 0, 0, 0, 0, successors[node.yes], 0);
                case 12 -> new Node(12, node.a, this.tables.add(this.indices.subList(node.b, node.b + node.c)), node.c, node.d, 0, 0);
                case 14 -> {
                    var table = new ArrayList<Integer>(256);
                    for (int i = 0; i < 256; i++) {
                        table.add(successors[this.indices.get(node.a + i)]);
                    }
                    yield new Node(14, this.tables.add(table), 0, 0, 0, 0, 0);
                }
                default -> new Node(node.op, node.a, node.b, node.c, node.d, successors[node.yes], successors[node.no]);
            };
            Integer previous = this.intern.get(mapped);
            if (previous != null) {
                return previous;
            }
            int index = this.nodes.size();
            this.nodes.add(mapped);
            this.intern.put(mapped, index);
            return index;
        }
    }
}
