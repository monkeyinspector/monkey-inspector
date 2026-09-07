package io.github.monkeyinspector;

import io.github.monkeyinspector.edit.EditCore;
import io.github.monkeyinspector.web.EditorApi;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone source editor; does not create a jME application or graphics context. */
public final class StaticInspector implements AutoCloseable {
    private final EditCore core;
    private final InspectorHttpServer server;
    public StaticInspector(Path root, InspectorConfig config) throws IOException {
        core = new EditCore(root, null);
        try {
            server = new InspectorHttpServer(config.host(), config.port(),
                    new AtomicReference<>("{\"status\":\"static\",\"scene\":[],\"appStates\":[],\"watchedObjects\":[]}"),
                    new InspectorHttpServer.Commands() {
                        public void clearTrace() { }
                        public void clearEngineProfile() { }
                        public void refreshSnapshot() { }
                    });
            server.installEditor(new EditorApi(core, null, null));
            server.start();
        } catch (IOException | RuntimeException e) { core.close(); throw e; }
    }
    @Override public void close() { server.close(); core.close(); }
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Usage: StaticInspector <source-root> [port]");
        var inspector = new StaticInspector(Path.of(args[0]), InspectorConfig.defaults().withPort(args.length == 2 ? Integer.parseInt(args[1]) : 7331));
        Runtime.getRuntime().addShutdownHook(new Thread(inspector::close));
        System.out.println("Monkey Inspector 0.4.1 STATIC — source editing available");
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
