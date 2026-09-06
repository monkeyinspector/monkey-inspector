package io.github.monkeyinspector;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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

    InspectorHttpServer(
            String host,
            int port,
            AtomicReference<String> snapshot,
            Commands commands
    ) throws IOException {

        this.snapshot = snapshot;
        this.commands = commands;

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
                Executors.newCachedThreadPool(
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
        server.start();
    }

    private void handleIndex(
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