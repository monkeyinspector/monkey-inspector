package io.github.monkeyinspector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public final class InspectorTrace {

    private InspectorTrace() {}

    private static final ThreadLocal<Deque<Frame>>
            STACK =
            ThreadLocal.withInitial(
                    ArrayDeque::new
            );

    private static final ConcurrentHashMap<
            String,
            NodeStats
            > NODES =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<
            EdgeKey,
            EdgeStats
            > EDGES =
            new ConcurrentHashMap<>();

    private static final AtomicLong
            STARTED_AT_MILLIS =
            new AtomicLong(
                    System.currentTimeMillis()
            );

    public static Span begin(
            String name
    ) {
        if (
                name == null
                        || name.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "trace name must not be blank"
            );
        }

        Deque<Frame> stack =
                STACK.get();

        Frame parent =
                stack.peek();

        Frame frame =
                new Frame(
                        name,
                        parent == null
                                ? null
                                : parent.name,
                        System.nanoTime()
                );

        stack.push(frame);

        return new Span(frame);
    }

    public static void runSpan(
            String name,
            Runnable runnable
    ) {
        Span span =
                begin(name);

        try {
            runnable.run();
        } catch (
                RuntimeException
                | Error throwable
        ) {
            span.failed();
            throw throwable;
        } finally {
            span.close();
        }
    }

    public static <T> T callSpan(
            String name,
            Supplier<T> supplier
    ) {
        Span span =
                begin(name);

        try {
            return supplier.get();
        } catch (
                RuntimeException
                | Error throwable
        ) {
            span.failed();
            throw throwable;
        } finally {
            span.close();
        }
    }

    public static void clear() {
        NODES.clear();
        EDGES.clear();

        STARTED_AT_MILLIS.set(
                System.currentTimeMillis()
        );
    }

    static void writeJson(
            JsonWriter j
    ) {
        j.objectStart();

        j.name("startedAtMillis")
                .value(
                        STARTED_AT_MILLIS.get()
                );

        j.name("nodes")
                .arrayStart();

        List<Map.Entry<String, NodeStats>> nodes =
                new ArrayList<>(
                        NODES.entrySet()
                );

        nodes.sort(
                Map.Entry.comparingByKey()
        );

        for (
                Map.Entry<String, NodeStats> entry
                : nodes
        ) {
            NodeStats stats =
                    entry.getValue();

            j.objectStart();

            j.name("name")
                    .value(entry.getKey());

            j.name("calls")
                    .value(stats.calls.sum());

            j.name("totalNanos")
                    .value(
                            stats.totalNanos.sum()
                    );

            j.name("selfNanos")
                    .value(
                            stats.selfNanos.sum()
                    );

            j.name("maxNanos")
                    .value(
                            stats.maxNanos.get()
                    );

            j.name("failures")
                    .value(
                            stats.failures.sum()
                    );

            j.objectEnd();
        }

        j.arrayEnd();

        j.name("edges")
                .arrayStart();

        List<Map.Entry<EdgeKey, EdgeStats>> edges =
                new ArrayList<>(
                        EDGES.entrySet()
                );

        edges.sort(
                Comparator.comparing(
                        entry ->
                                entry
                                        .getKey()
                                        .parent
                                        + "\0"
                                        + entry
                                        .getKey()
                                        .child
                )
        );

        for (
                Map.Entry<EdgeKey, EdgeStats> entry
                : edges
        ) {
            EdgeKey edge =
                    entry.getKey();

            EdgeStats stats =
                    entry.getValue();

            j.objectStart();

            j.name("from")
                    .value(edge.parent);

            j.name("to")
                    .value(edge.child);

            j.name("calls")
                    .value(stats.calls.sum());

            j.name("totalNanos")
                    .value(
                            stats.totalNanos.sum()
                    );

            j.name("maxNanos")
                    .value(
                            stats.maxNanos.get()
                    );

            j.name("failures")
                    .value(
                            stats.failures.sum()
                    );

            j.objectEnd();
        }

        j.arrayEnd();

        j.objectEnd();
    }

    public static final class Span
            implements AutoCloseable {

        private Frame frame;
        private boolean failed;

        private Span(
                Frame frame
        ) {
            this.frame = frame;
        }

        public Span failed() {
            failed = true;
            return this;
        }

        @Override
        public void close() {
            Frame current =
                    frame;

            if (current == null)
                return;

            frame = null;

            long elapsed =
                    Math.max(
                            0L,
                            System.nanoTime()
                                    - current.startedAt
                    );

            long self =
                    Math.max(
                            0L,
                            elapsed
                                    - current.childNanos
                    );

            NODES.computeIfAbsent(
                    current.name,
                    ignored -> new NodeStats()
            ).add(
                    elapsed,
                    self,
                    failed
            );

            if (current.parent != null) {
                EDGES.computeIfAbsent(
                        new EdgeKey(
                                current.parent,
                                current.name
                        ),
                        ignored -> new EdgeStats()
                ).add(
                        elapsed,
                        failed
                );
            }

            Deque<Frame> stack =
                    STACK.get();

            if (
                    !stack.isEmpty()
                            && stack.peek()
                            == current
            ) {
                stack.pop();
            } else {
                stack.remove(current);
            }

            Frame parent =
                    stack.peek();

            if (parent != null)
                parent.childNanos += elapsed;

            if (stack.isEmpty())
                STACK.remove();
        }
    }

    private static final class Frame {
        final String name;
        final String parent;
        final long startedAt;

        long childNanos;

        Frame(
                String name,
                String parent,
                long startedAt
        ) {
            this.name = name;
            this.parent = parent;
            this.startedAt = startedAt;
        }
    }

    private static final class NodeStats {
        final LongAdder calls =
                new LongAdder();

        final LongAdder totalNanos =
                new LongAdder();

        final LongAdder selfNanos =
                new LongAdder();

        final LongAdder failures =
                new LongAdder();

        final LongAccumulator maxNanos =
                new LongAccumulator(
                        Long::max,
                        0L
                );

        void add(
                long total,
                long self,
                boolean failed
        ) {
            calls.increment();

            totalNanos.add(total);
            selfNanos.add(self);

            maxNanos.accumulate(total);

            if (failed)
                failures.increment();
        }
    }

    private static final class EdgeStats {
        final LongAdder calls =
                new LongAdder();

        final LongAdder totalNanos =
                new LongAdder();

        final LongAdder failures =
                new LongAdder();

        final LongAccumulator maxNanos =
                new LongAccumulator(
                        Long::max,
                        0L
                );

        void add(
                long nanos,
                boolean failed
        ) {
            calls.increment();
            totalNanos.add(nanos);

            maxNanos.accumulate(nanos);

            if (failed)
                failures.increment();
        }
    }

    private record EdgeKey(
            String parent,
            String child
    ) {}
}