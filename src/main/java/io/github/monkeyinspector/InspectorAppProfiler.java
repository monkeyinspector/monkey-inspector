package io.github.monkeyinspector;

import com.jme3.profile.AppProfiler;
import com.jme3.profile.AppStep;
import com.jme3.profile.SpStep;
import com.jme3.profile.VpStep;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InspectorAppProfiler
        implements AppProfiler {

    private final AppProfiler delegate;

    private final Map<String, Stats> nodes =
            new LinkedHashMap<>();

    private final Map<EdgeKey, Long> edges =
            new LinkedHashMap<>();

    private String currentAppStep =
            "unknown";

    private String lastEvent;

    private long lastStartedNanos;

    private long startedAtMillis =
            System.currentTimeMillis();

    private long frames;

    private long frameTimeTotalNanos;
    private long frameTimeMaxNanos;
    private long previousBeginFrameNanos;

    InspectorAppProfiler(
            AppProfiler delegate
    ) {
        this.delegate = delegate;
    }

    @Override
    public void appStep(
            AppStep step
    ) {
        long now =
                System.nanoTime();

        currentAppStep =
                step.name();

        if (step == AppStep.BeginFrame) {
            frames++;

            if (previousBeginFrameNanos != 0L) {
                long frame =
                        now - previousBeginFrameNanos;

                frameTimeTotalNanos += frame;

                frameTimeMaxNanos =
                        Math.max(
                                frameTimeMaxNanos,
                                frame
                        );
            }

            previousBeginFrameNanos =
                    now;
        }

        mark(
                "app/" + step.name(),
                now
        );

        if (delegate != null)
            delegate.appStep(step);
    }

    @Override
    public void appSubStep(
            String... additionalInfo
    ) {
        long now =
                System.nanoTime();

        String info =
                join(additionalInfo);

        mark(
                "app/"
                        + currentAppStep
                        + "/"
                        + (
                        info.isBlank()
                                ? "substep"
                                : info
                ),
                now
        );

        if (delegate != null)
            delegate.appSubStep(
                    additionalInfo
            );
    }

    @Override
    public void vpStep(
            VpStep step,
            ViewPort viewport,
            Bucket bucket
    ) {
        long now =
                System.nanoTime();

        String viewportName =
                viewport == null
                        || viewport.getName() == null
                        ? "<viewport>"
                        : viewport.getName();

        String name =
                "viewport/"
                        + viewportName
                        + "/"
                        + step.name();

        if (bucket != null)
            name += "/" + bucket.name();

        mark(
                name,
                now
        );

        if (delegate != null) {
            delegate.vpStep(
                    step,
                    viewport,
                    bucket
            );
        }
    }

    @Override
    public void spStep(
            SpStep step,
            String... additionalInfo
    ) {
        long now =
                System.nanoTime();

        String info =
                join(additionalInfo);

        String name =
                "sceneProcessor/"
                        + step.name()
                        + (
                        info.isBlank()
                                ? ""
                                : "/" + info
                );

        mark(
                name,
                now
        );

        if (delegate != null) {
            delegate.spStep(
                    step,
                    additionalInfo
            );
        }
    }

    synchronized void clear() {
        nodes.clear();
        edges.clear();

        lastEvent = null;
        lastStartedNanos = 0L;

        startedAtMillis =
                System.currentTimeMillis();

        frames = 0L;

        frameTimeTotalNanos = 0L;
        frameTimeMaxNanos = 0L;
        previousBeginFrameNanos = 0L;
    }

    synchronized void writeJson(
            JsonWriter j
    ) {
        finishCurrent(
                System.nanoTime(),
                false
        );

        j.objectStart();

        j.name("startedAtMillis")
                .value(startedAtMillis);

        j.name("frames")
                .value(frames);

        long measuredFrames =
                Math.max(
                        0L,
                        frames - 1L
                );

        j.name("averageFrameNanos")
                .value(
                        measuredFrames == 0
                                ? 0L
                                : frameTimeTotalNanos
                                / measuredFrames
                );

        j.name("maxFrameNanos")
                .value(frameTimeMaxNanos);

        j.name("nodes")
                .arrayStart();

        List<Map.Entry<String, Stats>> sortedNodes =
                new ArrayList<>(
                        nodes.entrySet()
                );

        sortedNodes.sort(
                Map.Entry.comparingByKey()
        );

        for (
                Map.Entry<String, Stats> entry
                : sortedNodes
        ) {
            Stats stats =
                    entry.getValue();

            j.objectStart();

            j.name("name")
                    .value(entry.getKey());

            j.name("calls")
                    .value(stats.calls);

            j.name("totalNanos")
                    .value(stats.totalNanos);

            j.name("maxNanos")
                    .value(stats.maxNanos);

            j.objectEnd();
        }

        j.arrayEnd();

        j.name("edges")
                .arrayStart();

        List<Map.Entry<EdgeKey, Long>> sortedEdges =
                new ArrayList<>(
                        edges.entrySet()
                );

        sortedEdges.sort(
                Comparator
                        .comparing(
                                (
                                        Map.Entry<
                                                EdgeKey,
                                                Long
                                                > entry
                                ) -> entry
                                        .getKey()
                                        .from
                        )
                        .thenComparing(
                                entry ->
                                        entry
                                                .getKey()
                                                .to
                        )
        );

        for (
                Map.Entry<EdgeKey, Long> entry
                : sortedEdges
        ) {
            j.objectStart();

            j.name("from")
                    .value(
                            entry
                                    .getKey()
                                    .from
                    );

            j.name("to")
                    .value(
                            entry
                                    .getKey()
                                    .to
                    );

            j.name("calls")
                    .value(
                            entry.getValue()
                    );

            j.objectEnd();
        }

        j.arrayEnd();

        j.objectEnd();
    }

    private synchronized void mark(
            String event,
            long now
    ) {
        String previous =
                lastEvent;

        finishCurrent(
                now,
                true
        );

        if (
                previous != null
                        && !previous.equals(event)
        ) {
            EdgeKey edge =
                    new EdgeKey(
                            previous,
                            event
                    );

            edges.merge(
                    edge,
                    1L,
                    Long::sum
            );
        }

        lastEvent =
                event;

        lastStartedNanos =
                now;

        nodes.computeIfAbsent(
                event,
                ignored -> new Stats()
        ).calls++;
    }

    private void finishCurrent(
            long now,
            boolean clearCurrent
    ) {
        if (
                lastEvent != null
                        && lastStartedNanos != 0L
        ) {
            long elapsed =
                    Math.max(
                            0L,
                            now - lastStartedNanos
                    );

            nodes.computeIfAbsent(
                    lastEvent,
                    ignored -> new Stats()
            ).addDuration(
                    elapsed
            );

            lastStartedNanos =
                    now;
        }

        if (clearCurrent) {
            lastEvent = null;
            lastStartedNanos = 0L;
        }
    }

    private static String join(
            String[] values
    ) {
        if (
                values == null
                        || values.length == 0
        ) {
            return "";
        }

        StringBuilder out =
                new StringBuilder();

        for (String value : values) {
            if (
                    value == null
                            || value.isBlank()
            ) {
                continue;
            }

            if (!out.isEmpty())
                out.append(" / ");

            out.append(value);
        }

        return out.toString();
    }

    private static final class Stats {
        long calls;
        long totalNanos;
        long maxNanos;

        void addDuration(
                long nanos
        ) {
            totalNanos += nanos;

            maxNanos =
                    Math.max(
                            maxNanos,
                            nanos
                    );
        }
    }

    private record EdgeKey(
            String from,
            String to
    ) {}
}