package io.github.monkeyinspector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Filter;
import io.github.monkeyinspector.web.EditorApi;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

final class InspectorHttpServer
        implements AutoCloseable {

    interface Commands {
        void clearTrace();
        void clearEngineProfile();
        void refreshSnapshot();
    }

    private final HttpServer server;
    private final ExecutorService executor;

    private final AtomicReference<String>
            snapshot;

    private final Commands commands;

    private final byte[] indexHtml;
    private final String host;
    private final int port;

    InspectorHttpServer(
            String host,
            int port,
            AtomicReference<String> snapshot,
            Commands commands
    ) throws IOException {

        this.snapshot = snapshot;
        this.commands = commands;
        this.host = host;
        this.port = port;

        this.indexHtml =
                loadResource(
                        "/inspector/index.html"
                );

        server =
                HttpServer.create(
                        new InetSocketAddress(
                                host,
                                port
                        ),
                        0
                );

        server.createContext(
                "/api/snapshot",
                this::handleSnapshot
        );

        server.createContext(
                "/api/trace/clear",
                exchange ->
                        handleCommand(
                                exchange,
                                commands::clearTrace
                        )
        );

        server.createContext(
                "/api/engine-profile/clear",
                exchange ->
                        handleCommand(
                                exchange,
                                commands::clearEngineProfile
                        )
        );

        server.createContext(
                "/api/snapshot/refresh",
                exchange ->
                        handleCommand(
                                exchange,
                                commands::refreshSnapshot
                        )
        );

        server.createContext(
                "/",
                this::handleIndex
        );

        executor =
                Executors.newFixedThreadPool(12,
                        runnable -> {
                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "monkey-inspector-http"
                                    );

                            thread.setDaemon(true);

                            return thread;
                        }
                );

        server.setExecutor(executor);
    }

    void start() {
        if (!java.net.InetAddress.getLoopbackAddress().getHostAddress().equals(host)
                && !host.equals("127.0.0.1") && !host.equals("::1") && !host.equals("localhost"))
            System.err.println("[MonkeyInspector] WARNING: remote bind enables source editing without authentication: " + host);
        server.start();
    }

    void installEditor(EditorApi api) {
        api.install(server);
        // HttpServer has no context enumeration. Apply the same guard to every known context.
        for (String path : java.util.List.of("/", "/api/snapshot", "/api/trace/clear", "/api/engine-profile/clear",
                "/api/snapshot/refresh", "/api/editor", "/api/source", "/api/edit", "/api/undo", "/api/redo", "/api/viewport")) {
            // Context-specific handlers also validate methods. A root filter cannot protect child contexts.
            server.removeContext(path);
            com.sun.net.httpserver.HttpHandler handler = switch (path) {
                case "/" -> this::handleIndex;
                case "/api/snapshot" -> this::handleSnapshot;
                case "/api/trace/clear" -> e -> handleCommand(e, commands::clearTrace);
                case "/api/engine-profile/clear" -> e -> handleCommand(e, commands::clearEngineProfile);
                case "/api/snapshot/refresh" -> e -> handleCommand(e, commands::refreshSnapshot);
                default -> api::handle;
            };
            server.createContext(path, handler).getFilters().add(new Filter() {
                @Override public String description() { return "Same-origin and Host protection"; }
                @Override public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                    String authority = exchange.getRequestHeaders().getFirst("Host");
                    String configured = (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
                    boolean local = host.equals("127.0.0.1") || host.equals("::1") || host.equals("localhost");
                    if (authority == null || !(authority.equalsIgnoreCase(configured) || local && authority.equalsIgnoreCase("localhost:" + port))) {
                        EditorApi.send(exchange, 403, java.util.Map.of("message", "Invalid Host")); return;
                    }
                    String origin = exchange.getRequestHeaders().getFirst("Origin");
                    String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
                    if (origin != null && !origin.equalsIgnoreCase("http://" + authority) || "cross-site".equals(site)) {
                        EditorApi.send(exchange, 403, java.util.Map.of("message", "Cross-origin request rejected")); return;
                    }
                    chain.doFilter(exchange);
                }
            });
        }
    }

    private void handleIndex(
            HttpExchange exchange
    ) throws IOException {

        if ("/editor.js".equals(exchange.getRequestURI().getPath()) && "GET".equals(exchange.getRequestMethod())) {
            send(exchange, 200, "text/javascript; charset=utf-8", loadResource("/inspector/editor.js")); return;
        }

        if (
                !"GET".equalsIgnoreCase(
                        exchange.getRequestMethod()
                )
        ) {
            sendText(
                    exchange,
                    405,
                    "Method Not Allowed"
            );
            return;
        }

        if (
                !"/".equals(
                        exchange
                                .getRequestURI()
                                .getPath()
                )
        ) {
            sendText(
                    exchange,
                    404,
                    "Not Found"
            );
            return;
        }

        send(
                exchange,
                200,
                "text/html; charset=utf-8",
                indexHtml
        );
    }

    private void handleSnapshot(
            HttpExchange exchange
    ) throws IOException {

        if (
                !"GET".equalsIgnoreCase(
                        exchange.getRequestMethod()
                )
        ) {
            sendText(
                    exchange,
                    405,
                    "Method Not Allowed"
            );
            return;
        }

        exchange
                .getResponseHeaders()
                .set(
                        "Cache-Control",
                        "no-store, max-age=0"
                );

        send(
                exchange,
                200,
                "application/json; charset=utf-8",
                snapshot
                        .get()
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }

    private void handleCommand(
            HttpExchange exchange,
            Runnable command
    ) throws IOException {

        if (
                !"POST".equalsIgnoreCase(
                        exchange.getRequestMethod()
                )
        ) {
            sendText(
                    exchange,
                    405,
                    "Method Not Allowed"
            );
            return;
        }

        command.run();

        send(
                exchange,
                202,
                "application/json; charset=utf-8",
                "{\"accepted\":true}"
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }

    private static void sendText(
            HttpExchange exchange,
            int status,
            String text
    ) throws IOException {

        send(
                exchange,
                status,
                "text/plain; charset=utf-8",
                text.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private static void send(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] body
    ) throws IOException {

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        contentType
                );

        exchange
                .getResponseHeaders()
                .set(
                        "X-Content-Type-Options",
                        "nosniff"
                );

        exchange
                .getResponseHeaders()
                .set(
                        "Referrer-Policy",
                        "no-referrer"
                );

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Security-Policy",
                        "default-src 'none'; "
                                + "style-src 'unsafe-inline'; "
                                + "script-src 'self' 'unsafe-inline'; "
                                + "connect-src 'self'; "
                                + "img-src 'self' data:"
                );

        exchange
                .getResponseHeaders()
                .set(
                        "X-Frame-Options",
                        "DENY"
                );

        exchange.sendResponseHeaders(
                status,
                body.length
        );

        try (
                var output =
                        exchange.getResponseBody()
        ) {
            output.write(body);
        } finally {
            exchange.close();
        }
    }

    private static byte[] loadResource(
            String path
    ) throws IOException {

        try (
                InputStream input =
                        InspectorHttpServer.class
                                .getResourceAsStream(path)
        ) {
            if (input == null) {
                throw new IOException(
                        "Missing resource "
                                + path
                );
            }

            return input.readAllBytes();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
