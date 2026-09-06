package io.github.monkeyinspector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.profile.AppProfiler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class InspectorState
        extends BaseAppState {

    public static final int DEFAULT_PORT =
            7331;

    private final InspectorConfig config;

    private final AtomicReference<String>
            snapshot =
            new AtomicReference<>(
                    "{\"status\":\"starting\"}"
            );

    private final CopyOnWriteArrayList<
            WatchedObject
            > watchedObjects =
            new CopyOnWriteArrayList<>();

    private Application application;

    private InspectorHttpServer server;

    private SnapshotBuilder snapshotBuilder;

    private InspectorAppProfiler engineProfiler;

    private AppProfiler previousProfiler;

    private float elapsed;

    private volatile boolean forceSnapshot;

    public InspectorState() {
        this(
                InspectorConfig.defaults()
        );
    }

    public InspectorState(
            InspectorConfig config
    ) {
        this.config = config;
    }

    public InspectorState(
            String host,
            int port,
            float snapshotIntervalSeconds,
            int maxSceneNodes
    ) {
        this(
                new InspectorConfig(
                        host,
                        port,
                        snapshotIntervalSeconds,
                        maxSceneNodes,
                        48,
                        true
                )
        );
    }

    public InspectorConfig getConfig() {
        return config;
    }

    public InspectorState watch(
            Object object
    ) {
        if (object == null)
            return this;

        return watch(
                object
                        .getClass()
                        .getSimpleName(),
                object
        );
    }

    public InspectorState watch(
            String name,
            Object object
    ) {
        if (object == null)
            return this;

        for (
                WatchedObject watched
                : watchedObjects
        ) {
            if (watched.object() == object)
                return this;
        }

        watchedObjects.add(
                new WatchedObject(
                        name == null
                                || name.isBlank()
                                ? object
                                .getClass()
                                .getSimpleName()
                                : name,
                        object
                )
        );

        return this;
    }

    public void unwatch(
            Object object
    ) {
        if (object == null)
            return;

        watchedObjects.removeIf(
                watched ->
                        watched.object()
                                == object
        );
    }

    public String getInspectorUrl() {
        return "http://"
                + config.host()
                + ":"
                + config.port()
                + "/";
    }

    public void refreshNow() {
        Application app =
                application;

        if (app == null)
            return;

        app.enqueue(
                () ->
                        forceSnapshot = true
        );
    }

    @Override
    protected void initialize(
            Application app
    ) {
        application = app;

        if (config.profileEngine()) {
            previousProfiler =
                    app.getAppProfiler();

            engineProfiler =
                    new InspectorAppProfiler(
                            previousProfiler
                    );

            app.setAppProfiler(
                    engineProfiler
            );
        }

        snapshotBuilder =
                new SnapshotBuilder(
                        app,
                        watchedObjects,
                        config.maxSceneNodes(),
                        config.maxFieldsPerObject(),
                        engineProfiler
                );

        snapshot.set(
                snapshotBuilder.build()
        );

        try {
            server =
                    new InspectorHttpServer(
                            config.host(),
                            config.port(),
                            snapshot,
                            new InspectorHttpServer.Commands() {

                                @Override
                                public void clearTrace() {
                                    app.enqueue(
                                            () -> {
                                                InspectorTrace.clear();
                                                forceSnapshot = true;
                                            }
                                    );
                                }

                                @Override
                                public void clearEngineProfile() {
                                    app.enqueue(
                                            () -> {
                                                if (
                                                        engineProfiler
                                                                != null
                                                ) {
                                                    engineProfiler.clear();
                                                }

                                                forceSnapshot = true;
                                            }
                                    );
                                }

                                @Override
                                public void refreshSnapshot() {
                                    app.enqueue(
                                            () ->
                                                    forceSnapshot = true
                                    );
                                }
                            }
                    );

            server.start();

            System.out.println(
                    "[MonkeyInspector 0.2] "
                            + getInspectorUrl()
            );

        } catch (IOException exception) {
            restoreProfiler(app);

            throw new IllegalStateException(
                    "Could not start Monkey Inspector on "
                            + getInspectorUrl(),
                    exception
            );
        }
    }

    @Override
    public void update(
            float tpf
    ) {
        elapsed += tpf;

        if (
                !forceSnapshot
                        && elapsed
                        < config.snapshotIntervalSeconds()
        ) {
            return;
        }

        elapsed = 0f;
        forceSnapshot = false;

        snapshot.set(
                snapshotBuilder.build()
        );
    }

    @Override
    protected void cleanup(
            Application app
    ) {
        if (server != null)
            server.close();

        restoreProfiler(app);

        server = null;
        snapshotBuilder = null;
        engineProfiler = null;
        previousProfiler = null;
        application = null;
    }

    private void restoreProfiler(
            Application app
    ) {
        if (
                engineProfiler != null
                        && app.getAppProfiler()
                        == engineProfiler
        ) {
            app.setAppProfiler(
                    previousProfiler
            );
        }
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
}