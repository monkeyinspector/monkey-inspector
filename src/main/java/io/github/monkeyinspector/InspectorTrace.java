package io.github.monkeyinspector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Lightweight explicit runtime tracing for gameplay/workflow code. */
public final class InspectorTrace {
    private InspectorTrace() {}

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ConcurrentHashMap<String, NodeStats> NODES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EdgeKey, EdgeStats> EDGES = new ConcurrentHashMap<>();

    public static Span begin(String name) {
        Deque<Frame> stack = STACK.get();
        String parent = stack.isEmpty() ? null : stack.peek().name;
        Frame frame = new Frame(name, parent, System.nanoTime());
        stack.push(frame);
        return new Span(frame);
    }

    public static void runSpan(String name, Runnable runnable) {
        try (Span ignored = begin(name)) { runnable.run(); }
    }

    public static <T> T callSpan(String name, Supplier<T> supplier) {
        try (Span ignored = begin(name)) { return supplier.get(); }
    }

    public static void clear() {
        NODES.clear();
        EDGES.clear();
    }

    static void writeJson(JsonWriter j) {
        j.objectStart();
        j.name("nodes").arrayStart();
        List<Map.Entry<String, NodeStats>> nodes = new ArrayList<>(NODES.entrySet());
        nodes.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, NodeStats> e : nodes) {
            NodeStats s = e.getValue();
            j.objectStart()
                .name("name").value(e.getKey())
                .name("calls").value(s.calls.sum())
                .name("totalNanos").value(s.totalNanos.sum())
                .objectEnd();
        }
        j.arrayEnd();

        j.name("edges").arrayStart();
        List<Map.Entry<EdgeKey, EdgeStats>> edges = new ArrayList<>(EDGES.entrySet());
        edges.sort(Comparator.comparing(e -> e.getKey().parent + "\0" + e.getKey().child));
        for (Map.Entry<EdgeKey, EdgeStats> e : edges) {
            EdgeStats s = e.getValue();
            j.objectStart()
                .name("from").value(e.getKey().parent)
                .name("to").value(e.getKey().child)
                .name("calls").value(s.calls.sum())
                .name("totalNanos").value(s.totalNanos.sum())
                .objectEnd();
        }
        j.arrayEnd();
        j.objectEnd();
    }

    public static final class Span implements AutoCloseable {
        private Frame frame;
        private Span(Frame frame) { this.frame = frame; }

        @Override public void close() {
            Frame f = frame;
            if (f == null) return;
            frame = null;
            long elapsed = System.nanoTime() - f.startedAt;

            NODES.computeIfAbsent(f.name, k -> new NodeStats()).add(elapsed);
            if (f.parent != null) {
                EDGES.computeIfAbsent(new EdgeKey(f.parent, f.name), k -> new EdgeStats()).add(elapsed);
            }

            Deque<Frame> stack = STACK.get();
            if (!stack.isEmpty() && stack.peek() == f) stack.pop();
            else stack.remove(f);
            if (stack.isEmpty()) STACK.remove();
        }
    }

    private static final class Frame {
        final String name;
        final String parent;
        final long startedAt;
        Frame(String name, String parent, long startedAt) {
            this.name = name; this.parent = parent; this.startedAt = startedAt;
        }
    }

    private static final class NodeStats {
        final LongAdder calls = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
        void add(long nanos) { calls.increment(); totalNanos.add(nanos); }
    }

    private static final class EdgeStats {
        final LongAdder calls = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
        void add(long nanos) { calls.increment(); totalNanos.add(nanos); }
    }

    private static final class EdgeKey {
        final String parent;
        final String child;
        EdgeKey(String parent, String child) { this.parent = parent; this.child = child; }
        @Override public boolean equals(Object o) {
            return o instanceof EdgeKey && parent.equals(((EdgeKey)o).parent) && child.equals(((EdgeKey)o).child);
        }
        @Override public int hashCode() { return 31 * parent.hashCode() + child.hashCode(); }
    }
}
