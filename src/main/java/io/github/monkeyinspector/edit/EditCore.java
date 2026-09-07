package io.github.monkeyinspector.edit;

import io.github.monkeyinspector.source.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static io.github.monkeyinspector.edit.EditTransaction.Status.*;

/** Shared property/viewport/static transaction engine. Files and sessions have one worker owner. */
public final class EditCore implements AutoCloseable {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monkey-inspector-source"); t.setDaemon(true); return t;
    });
    private final RuntimeEditor runtime;
    private final SourceScanner scanner;
    private final SourcePatcher patcher;
    private SourceWatcher watcher;
    private final UndoManager history = new UndoManager(100);
    private volatile Map<String, SourceBinding> bindings = Map.of();
    private volatile String sourceError = "";
    private volatile int undoCount, redoCount;
    private EditSession session;
    private boolean deferredReload;

    public EditCore(Path root, RuntimeEditor runtime) throws IOException {
        this.runtime = runtime;
        scanner = root == null ? null : new SourceScanner(root);
        patcher = scanner == null ? null : new SourcePatcher(scanner);
        if (scanner != null) {
            worker.execute(() -> reload(false));
            try { watcher = new SourceWatcher(scanner.root(), () -> worker.execute(() -> reload(true))); }
            catch (IOException e) { worker.shutdownNow(); throw e; }
        }
        worker.scheduleWithFixedDelay(() -> {
            if (session != null && session.started().plusSeconds(30).isBefore(Instant.now())) cancel();
        }, 5, 5, TimeUnit.SECONDS);
    }
    public boolean live() { return runtime != null; }
    public Map<String, SourceBinding> bindings() { return bindings; }
    public String sourceError() { return sourceError; }
    public int undoCount() { return undoCount; }
    public int redoCount() { return redoCount; }
    public <T> CompletableFuture<T> submit(Callable<T> action) {
        var future = new CompletableFuture<T>();
        worker.execute(() -> {
            try { future.complete(action.call()); }
            catch (Throwable e) { future.completeExceptionally(e); }
        });
        return future;
    }
    public CompletableFuture<EditTransaction.Result> execute(EditCommand command) {
        return submit(() -> apply(command));
    }
    private EditTransaction.Result result(EditTransaction.Status status, String message) {
        return new EditTransaction.Result(status, session == null ? null : session.id(), message);
    }
    private void reload(boolean synchronize) {
        if (scanner == null) return;
        if (session != null) { deferredReload = true; return; }
        try {
            var previous = bindings;
            var next = scanner.scan();
            bindings = next; sourceError = "";
            if (synchronize && runtime != null) {
                for (var binding : next.values()) {
                    var old = previous.get(binding.id());
                    if (old == null || old.revision().equals(binding.revision())) continue;
                    for (var property : binding.literals().entrySet()) {
                        var oldValue = old.literals().get(property.getKey());
                        if (oldValue != null && oldValue.value().equals(property.getValue().value())) continue;
                        try { runtime.write(binding.id(), property.getKey(), property.getValue().value()); }
                        catch (Exception e) { sourceError = "Source updated; runtime synchronization failed: " + e.getMessage(); }
                    }
                }
            }
        } catch (IOException e) { sourceError = e.getMessage(); bindings = Map.of(); }
    }
    private void finished() {
        session = null;
        if (deferredReload) { deferredReload = false; reload(true); }
        undoCount = history.undoCount(); redoCount = history.redoCount();
    }
    private EditTransaction.Result begin(EditCommand c) throws Exception {
        if (session != null) return result(SOURCE_CONFLICT, "Another edit is active");
        Objects.requireNonNull(c.property(), "property");
        if (c.mode() != EditMode.LIVE) {
            var binding = bindings.get(c.editableId());
            if (binding == null) return result(INVALID_BINDING, "No valid source binding. Use LIVE for runtime-only editing.");
            if (!binding.revision().hash().equals(c.expectedSourceRevision()) ||
                    !SourceRevision.of(java.nio.file.Files.readString(scanner.validate(binding.sourceFile()))).hash().equals(c.expectedSourceRevision()))
                return result(SOURCE_CONFLICT, "Source changed externally. Reload before editing.");
            if (!binding.literals().containsKey(c.property()))
                return result(COMPUTED_SOURCE, "Computed source. Runtime editable; source editing locked.");
        }
        if (runtime == null && c.mode() != EditMode.SOURCE)
            return result(INVALID_BINDING, "Game disconnected. Select SOURCE mode.");
        TransformValue before = c.mode() == EditMode.SOURCE ? null : runtime.read(c.editableId(), c.property());
        session = new EditSession(UUID.randomUUID().toString(), c.editableId(), c.property(), c.mode(),
                before, c.expectedSourceRevision(), Instant.now());
        return result(SUCCESS, "Modified");
    }
    private EditTransaction.Result apply(EditCommand c) throws Exception {
        if (c.phase() == EditPhase.BEGIN) return begin(c);
        if (c.phase() == EditPhase.COMMIT && c.sessionId() == null) {
            var started = begin(c);
            if (started.status() != SUCCESS) return started;
        } else if (session == null || !session.id().equals(c.sessionId())) {
            return result(SOURCE_CONFLICT, "Unknown or expired edit session");
        }
        if (c.phase() == EditPhase.CANCEL) return cancel();
        var s = session;
        try {
            Objects.requireNonNull(c.value(), "value").validate(s.property());
            if (c.phase() == EditPhase.PREVIEW) {
                if (s.before() != null) runtime.write(s.editableId(), s.property(), c.value());
                return result(SUCCESS, "Modified");
            }
            SourcePatcher.Change change = null;
            if (s.before() != null) runtime.write(s.editableId(), s.property(), c.value());
            if (s.mode() != EditMode.LIVE) {
                change = patcher.patch(bindings.get(s.editableId()), s.property(), c.value(), s.expectedRevision());
            }
            history.push(new EditTransaction(s.editableId(), s.property(), s.before(),
                    s.before() == null ? null : c.value(), change, Instant.now()));
            // Adopt our revision before queued watch events, suppressing self-write feedback.
            session = null; reload(false); finished();
            return result(s.mode() == EditMode.LIVE ? RUNTIME_ONLY : SUCCESS,
                    s.mode() == EditMode.LIVE ? "Runtime only" : "Source synchronized");
        } catch (Exception e) {
            String rollback = "";
            if (s.before() != null) {
                try { runtime.write(s.editableId(), s.property(), s.before()); }
                catch (Exception failed) { rollback = "; Runtime rollback failed: " + failed.getMessage(); }
            }
            finished();
            var status = e instanceof SourceConflictException ? SOURCE_CONFLICT :
                    e instanceof IOException ? SOURCE_WRITE_FAILED : RUNTIME_FAILED;
            return result(status, e.getMessage() + rollback);
        }
    }
    private EditTransaction.Result cancel() {
        if (session == null) return result(CANCELLED, "Cancelled");
        try {
            if (session.before() != null) runtime.write(session.editableId(), session.property(), session.before());
            finished(); return result(CANCELLED, "Cancelled");
        } catch (Exception e) { finished(); return result(RUNTIME_FAILED, "Cancel rollback failed: " + e.getMessage()); }
    }
    public CompletableFuture<EditTransaction.Result> undo(boolean redo) {
        return submit(() -> {
            if (session != null) return result(SOURCE_CONFLICT, "Finish the current edit first");
            var entry = history.peek(redo);
            if (entry == null) return result(SUCCESS, "History is empty");
            TransformValue current = null;
            boolean runtimeChanged = false;
            try {
                if (entry.runtimeBefore() != null) {
                    current = runtime.read(entry.editableId(), entry.property());
                    var expected = redo ? entry.runtimeBefore() : entry.runtimeAfter();
                    if (!current.equals(expected)) return result(SOURCE_CONFLICT, "Runtime changed since this transaction");
                    runtime.write(entry.editableId(), entry.property(), redo ? entry.runtimeAfter() : entry.runtimeBefore());
                    runtimeChanged = true;
                }
                if (entry.source() != null) {
                    var change = entry.source();
                    patcher.replace(change.file(), redo ? change.before() : change.after(), redo ? change.after() : change.before());
                }
                history.moved(redo); reload(false); finished();
                return result(entry.source() == null ? RUNTIME_ONLY : SUCCESS, redo ? "Redone" : "Undone");
            } catch (Exception e) {
                String message = e.getMessage();
                if (runtimeChanged) {
                    try { runtime.write(entry.editableId(), entry.property(), current); }
                    catch (Exception failed) { message += "; Runtime rollback failed: " + failed.getMessage(); }
                }
                return result(e instanceof SourceConflictException ? SOURCE_CONFLICT : SOURCE_WRITE_FAILED, message);
            }
        });
    }
    @Override public void close() {
        try { if (watcher != null) watcher.close(); } catch (IOException ignored) { }
        worker.shutdownNow();
    }
}
