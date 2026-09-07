package io.github.monkeyinspector;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.scene.Spatial;
import io.github.monkeyinspector.edit.*;
import io.github.monkeyinspector.viewport.*;
import io.github.monkeyinspector.web.EditorApi;
import java.nio.file.Path;
import java.util.*;
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
            "0.4.1";

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

    private final EditableRegistry editables = new EditableRegistry();
    private Path sourceRoot = Path.of(System.getProperty("user.dir"));
    private ViewportConfig viewportConfig = ViewportConfig.defaults();
    private EditCore editCore;
    private EditorApi editorApi;
    private ViewportCaptureProcessor capture;

    /** Configure before attachment. A null root disables source editing. */
    public InspectorState sourceRoot(Path root) {
        if (application != null) throw new IllegalStateException("Configure source root before attachment");
        sourceRoot = root; return this;
    }

    /** Configure capture before attachment. */
    public InspectorState viewport(ViewportConfig value) {
        if (application != null) throw new IllegalStateException("Configure viewport before attachment");
        viewportConfig = Objects.requireNonNull(value); return this;
    }

    /** Register on the application thread, including before this state is attached. */
    public InspectorState editable(String id, Spatial spatial) {
        if (application == null) editables.register(id, spatial);
        else application.enqueue(() -> { editables.register(id, spatial); forceSnapshot = true; });
        return this;
    }

    private void publishEditorSnapshot() {
        if (editorApi == null) return;
        List<Map<String,Object>> objects = new ArrayList<>();
        for (var handle : editables.handles()) {
            Spatial spatial = handle.spatial();
            var t = spatial.getLocalTranslation(); var s = spatial.getLocalScale(); var r = spatial.getLocalRotation();
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("id", handle.id());
            item.put("sceneId", spatial.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(spatial)));
            item.put("className", spatial.getClass().getName());
            item.put("values", Map.of("localTranslation", List.of(t.x,t.y,t.z), "localScale", List.of(s.x,s.y,s.z),
                    "localRotation", List.of(r.getX(),r.getY(),r.getZ(),r.getW())));
            var screen = application.getCamera().getScreenCoordinates(spatial.getWorldTranslation());
            item.put("screen", List.of(screen.x / application.getCamera().getWidth(),
                    1 - screen.y / application.getCamera().getHeight(), screen.z));
            objects.add(Collections.unmodifiableMap(item));
        }
        editorApi.publish(objects);
    }

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
            var runtime = new JmeRuntimeEditor(app, editables);
            editCore = new EditCore(sourceRoot, runtime);
            ViewportEditor viewport = null;
            FrameHub frames = null;
            if (app instanceof SimpleApplication simple && viewportConfig.enabled()) {
                frames = new FrameHub();
                var picking = new PickingService(app.getCamera(), simple.getRootNode(), editables);
                viewport = new ViewportEditor(runtime, picking, new DragController(app.getCamera(), picking, editables));
                capture = new ViewportCaptureProcessor(viewportConfig, frames);
                app.getViewPort().addProcessor(capture);
            }
            editorApi = new EditorApi(editCore, viewport, frames);
            publishEditorSnapshot();
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

            server.installEditor(editorApi);
            server.start();

            System.out.println(
                    "[MonkeyInspector "
                            + VERSION
                            + "] "
                            + getInspectorUrl()
            );

        } catch (IOException exception) {
            if (server != null) server.close();
            if (capture != null) app.getViewPort().removeProcessor(capture);
            if (editCore != null) editCore.close();
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
        publishEditorSnapshot();
    }

    @Override
    protected void cleanup(
            Application app
    ) {
        if (server != null)
            server.close();

        if (capture != null) { app.getViewPort().removeProcessor(capture); capture.cleanup(); }
        if (editCore != null) editCore.close();
        capture = null; editCore = null; editorApi = null;

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
