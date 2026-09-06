package io.github.monkeyinspector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.profile.AppProfiler;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * jMonkeyEngine app state that exposes a live runtime inspector over HTTP.
 * Attach one instance to an application's state manager, then open
 * {@link #getInspectorUrl()} in a browser.
 */
public final class InspectorState
        extends BaseAppState {

    /** Current Monkey Inspector release. */
    public static final String VERSION =
            "0.3.0";

    /** Default HTTP port. */
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

    /**
     * Creates an inspector with {@link InspectorConfig#defaults()}.
     */
    public InspectorState() {
        this(
                InspectorConfig.defaults()
        );
    }

    /**
     * Creates an inspector with the supplied configuration.
     *
     * @param config inspector configuration
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public InspectorState(
            InspectorConfig config
    ) {
        this.config = Objects.requireNonNull(
                config,
                "config"
        );
    }

    /**
     * Creates an inspector using the common configuration options.
     * Engine profiling is enabled and at most 48 fields are shown per object.
     *
     * @param host HTTP bind address
     * @param port HTTP port
     * @param snapshotIntervalSeconds positive snapshot interval in seconds
     * @param maxSceneNodes positive maximum scene-node count
     */
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

    /**
     * Returns this inspector's immutable configuration.
     *
     * @return inspector configuration
     */
    public InspectorConfig getConfig() {
        return config;
    }

    /**
     * Adds an object to the watched-object section using its simple class name.
     * Repeated calls for the same object identity are ignored.
     *
     * @param object object to watch; {@code null} is ignored
     * @return this state for chaining
     */
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

    /**
     * Adds a named object to the watched-object section.
     * Repeated calls for the same object identity are ignored.
     *
     * @param name display name; a blank value uses the simple class name
     * @param object object to watch; {@code null} is ignored
     * @return this state for chaining
     */
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

    /**
     * Removes an object from the watched-object section by identity.
     *
     * @param object object to remove; {@code null} is ignored
     */
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

    /**
     * Returns the browser URL for this inspector.
     *
     * @return inspector URL
     */
    public String getInspectorUrl() {
        String host =
                config.host();

        if (host.contains(":")
                && !host.startsWith("[")) {
            host = "[" + host + "]";
        }

        return "http://"
                + host
                + ":"
                + config.port()
                + "/";
    }

    /**
     * Requests a fresh snapshot on the application thread.
     * The request is ignored before initialization and after cleanup.
     */
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
                    "[MonkeyInspector "
                            + VERSION
                            + "] "
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
