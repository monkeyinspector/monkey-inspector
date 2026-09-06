package io.github.monkeyinspector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

final class InspectorHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final AtomicReference<String> snapshot;
    private final byte[] indexHtml;

    InspectorHttpServer(String host, int port, AtomicReference<String> snapshot) throws IOException {
        this.snapshot = snapshot;
        this.indexHtml = loadResource("/inspector/index.html");
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/snapshot", this::handleSnapshot);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "monkey-inspector-http");
            t.setDaemon(true);
            return t;
        }));
    }

    void start() { server.start(); }

    private void handleIndex(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8)); return; }
        send(ex, 200, "text/html; charset=utf-8", indexHtml);
    }

    private void handleSnapshot(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8)); return; }
        byte[] body = snapshot.get().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, 200, "application/json; charset=utf-8", body);
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private static byte[] loadResource(String name) throws IOException {
        try (InputStream in = InspectorHttpServer.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("Missing resource " + name);
            return in.readAllBytes();
        }
    }

    @Override public void close() { server.stop(0); }
}
