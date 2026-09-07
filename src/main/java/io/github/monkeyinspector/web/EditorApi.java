package io.github.monkeyinspector.web;

import com.sun.net.httpserver.*;
import io.github.monkeyinspector.edit.*;
import io.github.monkeyinspector.source.*;
import io.github.monkeyinspector.viewport.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** HTTP only sees immutable runtime snapshots. All file work is submitted to EditCore. */
public final class EditorApi {
    private final EditCore core;
    private final ViewportEditor viewport;
    private final FrameHub frames;
    private volatile List<Map<String,Object>> objects = List.of();
    private final Object mutationLock = new Object();
    public EditorApi(EditCore core, ViewportEditor viewport, FrameHub frames) { this.core = core; this.viewport = viewport; this.frames = frames; }
    public void publish(List<Map<String,Object>> objects) { this.objects = List.copyOf(objects); }
    public void install(HttpServer server) {
        for (String path : List.of("/api/editor", "/api/source", "/api/edit", "/api/undo", "/api/redo", "/api/viewport"))
            server.createContext(path, this::handle);
    }
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/api/viewport/mjpeg")) {
                if (!method(exchange, "GET")) return;
                stream(exchange); return;
            }
            if (path.equals("/api/editor") || path.equals("/api/source/status") || path.startsWith("/api/source/")) {
                if (!method(exchange, "GET")) return;
                if (path.equals("/api/editor") || path.equals("/api/source/status")) send(exchange, 200, snapshot());
                else {
                    var binding = core.bindings().get(path.substring("/api/source/".length()));
                    send(exchange, binding == null ? 404 : 200, binding == null ? Map.of("message", "Unknown binding") : source(binding));
                }
                return;
            }
            Set<String> mutations = Set.of("/api/edit", "/api/edit/begin", "/api/edit/preview", "/api/edit/commit", "/api/edit/cancel", "/api/undo", "/api/redo", "/api/viewport/pick", "/api/viewport/select", "/api/viewport/drag/begin", "/api/viewport/drag/preview", "/api/viewport/drag/commit", "/api/viewport/drag/cancel");
            if (!mutations.contains(path)) { send(exchange, 404, Map.of("message", "Not found")); return; }
            if (!method(exchange, "POST")) return;
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.split(";")[0].trim().equalsIgnoreCase("application/json")) {
                send(exchange, 415, Map.of("message", "Use application/json")); return;
            }
            byte[] bytes = exchange.getRequestBody().readNBytes(16_385);
            if (bytes.length > 16_384) { send(exchange, 413, Map.of("message", "Request too large")); return; }
            var body = Json.object(new String(bytes, StandardCharsets.UTF_8));
            Object response;
            synchronized (mutationLock) { response = mutate(path, body); }
            if (response instanceof EditTransaction.Result result) {
                int status = switch (result.status()) {
                    case SUCCESS, RUNTIME_ONLY, CANCELLED -> 200;
                    case SOURCE_CONFLICT -> 409;
                    case SOURCE_WRITE_FAILED, RUNTIME_FAILED -> 500;
                    default -> 422;
                };
                var map = new LinkedHashMap<String,Object>();
                map.put("status", result.status()); map.put("message", result.message()); map.put("sessionId", result.sessionId());
                send(exchange, status, map);
            } else send(exchange, 200, response);
        } catch (IllegalArgumentException | NullPointerException e) { send(exchange, 400, Map.of("message", String.valueOf(e.getMessage()))); }
        catch (Exception e) { send(exchange, 500, Map.of("message", String.valueOf(e.getMessage()))); }
    }
    private Object mutate(String path, Map<String,Object> body) throws Exception {
        if (path.equals("/api/undo") || path.equals("/api/redo")) return await(core.undo(path.endsWith("redo")));
        if (path.startsWith("/api/viewport/")) {
            if (viewport == null) throw new IllegalArgumentException("Viewport unavailable in static mode");
            if (path.endsWith("/pick")) {
                String id = await(core.submit(() -> viewport.pick(number(body, "x"), number(body, "y"))));
                return Collections.singletonMap("editableId", id);
            }
            if (path.endsWith("/select")) {
                await(core.submit(() -> { viewport.select(string(body, "editableId")); return null; }));
                return Map.of("status", "SUCCESS");
            }
        }
        EditPhase phase = path.equals("/api/edit") ? EditPhase.COMMIT : EditPhase.valueOf(path.substring(path.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT));
        EditMode mode = body.get("mode") == null ? EditMode.BOTH : EditMode.valueOf(string(body, "mode"));
        String id = string(body, "editableId"), session = string(body, "sessionId");
        TransformProperty property = body.get("property") == null ? TransformProperty.localTranslation : TransformProperty.valueOf(string(body, "property"));
        TransformValue value = null;
        boolean drag = path.startsWith("/api/viewport/drag/");
        if (drag && (phase == EditPhase.PREVIEW || phase == EditPhase.COMMIT))
            value = await(core.submit(() -> viewport.position(number(body, "x"), number(body, "y"))));
        else if (body.get("value") instanceof List<?> list) {
            var values = new ArrayList<Float>();
            for (var item : list) { if (!(item instanceof Number n)) throw new IllegalArgumentException("value must be numbers"); values.add(n.floatValue()); }
            value = new TransformValue(values);
        }
        var result = await(core.execute(new EditCommand(id, property, value, mode, phase, session, string(body, "expectedSourceRevision"))));
        if (drag && phase == EditPhase.BEGIN && result.status() == EditTransaction.Status.SUCCESS) {
            try { await(core.submit(() -> { viewport.begin(id, number(body, "x"), number(body, "y")); return null; })); }
            catch (Exception e) {
                await(core.execute(new EditCommand(id, property, null, mode, EditPhase.CANCEL, result.sessionId(), null))); throw e;
            }
        }
        if (drag && (phase == EditPhase.COMMIT || phase == EditPhase.CANCEL))
            await(core.submit(() -> { viewport.end(); return null; }));
        return result;
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception {
        try { return future.get(); }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }
    private static String string(Map<String,Object> body, String name) {
        Object value = body.get(name);
        if (value == null) return null;
        if (!(value instanceof String s)) throw new IllegalArgumentException(name + " must be a string");
        return s;
    }
    private static float number(Map<String,Object> body, String name) {
        if (!(body.get(name) instanceof Number n)) throw new IllegalArgumentException("Missing " + name);
        return n.floatValue();
    }
    private Map<String,Object> source(SourceBinding b) {
        Map<String,Object> values = new LinkedHashMap<>();
        b.literals().forEach((p,l) -> values.put(p.name(), l.value().values()));
        return Map.of("id", b.id(), "file", b.sourceFile().getFileName().toString(), "startLine", b.startLine(),
                "endLine", b.endLine(), "revision", b.revision().hash(), "code", b.code(), "values", values);
    }
    private Map<String,Object> snapshot() {
        return Map.of("mode", core.live() ? "LIVE" : "STATIC", "objects", objects,
                "bindings", core.bindings().values().stream().map(this::source).toList(),
                "sourceError", core.sourceError(), "undoCount", core.undoCount(), "redoCount", core.redoCount(),
                "viewport", viewport != null && frames != null);
    }
    private void stream(HttpExchange exchange) throws IOException {
        if (frames == null || !frames.subscribe()) { send(exchange, 503, Map.of("message", "Viewport unavailable or viewer limit reached")); return; }
        exchange.getResponseHeaders().set("Content-Type", "multipart/x-mixed-replace; boundary=mi-frame");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        try {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                long sequence = 0;
                while (!frames.closed() && !Thread.currentThread().isInterrupted()) {
                    var frame = frames.await(sequence);
                    if (frame == null) continue;
                    byte[] jpeg = frame.jpeg();
                    out.write(("--mi-frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + jpeg.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(jpeg); out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); sequence = frame.sequence();
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException ignored) { /* browser disconnected */ }
        finally { frames.unsubscribe(); exchange.close(); }
    }
    private static boolean method(HttpExchange exchange, String method) throws IOException {
        if (exchange.getRequestMethod().equals(method)) return true;
        exchange.getResponseHeaders().set("Allow", method);
        send(exchange, 405, Map.of("message", "Method not allowed")); return false;
    }
    public static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); } finally { exchange.close(); }
    }
}
