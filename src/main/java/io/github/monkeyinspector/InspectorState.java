package io.github.monkeyinspector;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Attach this AppState to a jMonkeyEngine application to expose a local-only live inspector.
 */
public final class InspectorState extends BaseAppState {
    public static final int DEFAULT_PORT = 7331;

    private final String host;
    private final int port;
    private final float snapshotIntervalSeconds;
    private final int maxSceneNodes;
    private final AtomicReference<String> snapshot = new AtomicReference<>("{\"status\":\"starting\"}");
    private final CopyOnWriteArrayList<Object> watchedObjects = new CopyOnWriteArrayList<>();
    private InspectorHttpServer server;
    private SnapshotBuilder snapshotBuilder;
    private float elapsed;

    public InspectorState() { this("127.0.0.1", DEFAULT_PORT, 0.25f, 20_000); }

    public InspectorState(String host, int port, float snapshotIntervalSeconds, int maxSceneNodes) {
        this.host = host;
        this.port = port;
        this.snapshotIntervalSeconds = snapshotIntervalSeconds;
        this.maxSceneNodes = maxSceneNodes;
    }

    /** Add arbitrary subsystem/service objects to the Objects panel. */
    public InspectorState watch(Object object) {
        if (object != null) watchedObjects.addIfAbsent(object);
        return this;
    }

    public void unwatch(Object object) { watchedObjects.remove(object); }

    public String getInspectorUrl() { return "http://" + host + ":" + port + "/"; }

    @Override protected void initialize(Application app) {
        snapshotBuilder = new SnapshotBuilder(app, watchedObjects, maxSceneNodes);
        snapshot.set(snapshotBuilder.build());
        try {
            server = new InspectorHttpServer(host, port, snapshot);
            server.start();
            System.out.println("[MonkeyInspector] " + getInspectorUrl());
        } catch (IOException e) {
            throw new IllegalStateException("Could not start Monkey Inspector on " + getInspectorUrl(), e);
        }
    }

    @Override public void update(float tpf) {
        elapsed += tpf;
        if (elapsed < snapshotIntervalSeconds) return;
        elapsed = 0f;
        snapshot.set(snapshotBuilder.build());
    }

    @Override protected void cleanup(Application app) {
        if (server != null) server.close();
        server = null;
        snapshotBuilder = null;
    }

    @Override protected void onEnable() {}
    @Override protected void onDisable() {}
}
