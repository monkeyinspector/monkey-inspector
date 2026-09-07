package io.github.monkeyinspector.edit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static io.github.monkeyinspector.edit.EditTransaction.Status.*;
import static io.github.monkeyinspector.edit.TransformProperty.localTranslation;
import static org.junit.jupiter.api.Assertions.*;

class EditCoreTest {
    @TempDir Path root;
    final String original = "// @mi-bind school\nschool.setLocalTranslation(1f, 2f, 3f)\n// @mi-end\n";
    static class Runtime implements RuntimeEditor {
        volatile TransformValue value = TransformValue.of(1,2,3);
        volatile int writes;
        public TransformValue read(String id, TransformProperty p) { return value; }
        public void write(String id, TransformProperty p, TransformValue v) { value = v; writes++; }
    }
    EditCore core(RuntimeEditor runtime) throws Exception {
        Files.writeString(root.resolve("World.kt"), original);
        var core = new EditCore(root, runtime);
        core.submit(() -> null).get(5, TimeUnit.SECONDS);
        return core;
    }
    EditTransaction.Result edit(EditCore core, EditPhase phase, String session, EditMode mode, TransformValue value, String revision) throws Exception {
        return core.execute(new EditCommand("school", localTranslation, value, mode, phase, session, revision)).get(5, TimeUnit.SECONDS);
    }
    String revision(EditCore core) { return core.bindings().get("school").revision().hash(); }
    @Test void fiftyPreviewsCommitOneUndoAndRedoRestoresBoth() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            var begin = edit(core, EditPhase.BEGIN, null, EditMode.BOTH, null, revision(core));
            assertEquals(SUCCESS, begin.status());
            for (int i = 0; i < 50; i++) edit(core, EditPhase.PREVIEW, begin.sessionId(), EditMode.BOTH, TransformValue.of(i,2,3), null);
            assertEquals(original, Files.readString(root.resolve("World.kt"))); assertEquals(0, core.undoCount());
            assertEquals(SUCCESS, edit(core, EditPhase.COMMIT, begin.sessionId(), EditMode.BOTH, TransformValue.of(80,2,3), null).status());
            assertEquals(1, core.undoCount());
            assertTrue(Files.readString(root.resolve("World.kt")).contains("80.0f"));
            assertEquals(SUCCESS, core.undo(false).get().status());
            assertEquals(original, Files.readString(root.resolve("World.kt"))); assertEquals(TransformValue.of(1,2,3), runtime.value);
            assertEquals(SUCCESS, core.undo(true).get().status()); assertEquals(TransformValue.of(80,2,3), runtime.value);
        }
    }
    @Test void cancelRestoresBeginStateWithoutSourceWrite() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            var begin = edit(core, EditPhase.BEGIN, null, EditMode.BOTH, null, revision(core));
            edit(core, EditPhase.PREVIEW, begin.sessionId(), EditMode.BOTH, TransformValue.of(8,9,10), null);
            assertEquals(CANCELLED, edit(core, EditPhase.CANCEL, begin.sessionId(), EditMode.BOTH, null, null).status());
            assertEquals(TransformValue.of(1,2,3), runtime.value); assertEquals(0, core.undoCount());
            assertEquals(original, Files.readString(root.resolve("World.kt")));
        }
    }
    @Test void externalChangeDuringDragConflictsAndPreservesFile() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            var begin = edit(core, EditPhase.BEGIN, null, EditMode.BOTH, null, revision(core));
            edit(core, EditPhase.PREVIEW, begin.sessionId(), EditMode.BOTH, TransformValue.of(8,9,10), null);
            String external = original + "// IntelliJ saved\n";
            Files.writeString(root.resolve("World.kt"), external);
            assertEquals(SOURCE_CONFLICT, edit(core, EditPhase.COMMIT, begin.sessionId(), EditMode.BOTH, TransformValue.of(8,9,10), null).status());
            assertEquals(external, Files.readString(root.resolve("World.kt"))); assertEquals(TransformValue.of(1,2,3), runtime.value);
            assertEquals(0, core.undoCount());
        }
    }
    @Test void liveAndSourceModesHaveDistinctEffects() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            assertEquals(RUNTIME_ONLY, edit(core, EditPhase.COMMIT, null, EditMode.LIVE, TransformValue.of(9,2,3), null).status());
            assertEquals(original, Files.readString(root.resolve("World.kt")));
            assertEquals(SUCCESS, edit(core, EditPhase.COMMIT, null, EditMode.SOURCE, TransformValue.of(20,2,3), revision(core)).status());
            assertEquals(TransformValue.of(9,2,3), runtime.value);
            core.submit(() -> null).get(); assertEquals(1, runtime.writes);
        }
    }
    @Test void staticModeSupportsSourceUndoRedo() throws Exception {
        try (var core = core(null)) {
            assertEquals(SUCCESS, edit(core, EditPhase.COMMIT, null, EditMode.SOURCE, TransformValue.of(7,8,9), revision(core)).status());
            assertEquals(SUCCESS, core.undo(false).get().status()); assertEquals(original, Files.readString(root.resolve("World.kt")));
            assertEquals(SUCCESS, core.undo(true).get().status());
        }
    }
    @Test void rejectsOverlappingAndUnknownSessions() throws Exception {
        try (var core = core(new Runtime())) {
            edit(core, EditPhase.BEGIN, null, EditMode.LIVE, null, null);
            assertEquals(SOURCE_CONFLICT, edit(core, EditPhase.BEGIN, null, EditMode.LIVE, null, null).status());
            assertEquals(SOURCE_CONFLICT, edit(core, EditPhase.CANCEL, "wrong", EditMode.LIVE, null, null).status());
        }
    }
    @Test void undoPreservesExternalFileEditAndRollsBackRuntime() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            edit(core, EditPhase.COMMIT, null, EditMode.BOTH, TransformValue.of(9,2,3), revision(core));
            String external = Files.readString(root.resolve("World.kt")) + "// external\n";
            Files.writeString(root.resolve("World.kt"), external);
            assertEquals(SOURCE_CONFLICT, core.undo(false).get().status());
            assertEquals(TransformValue.of(9,2,3), runtime.value); assertEquals(1, core.undoCount());
            assertEquals(external, Files.readString(root.resolve("World.kt")));
        }
    }
    @Test void watcherSynchronizesExternalLiteralChange() throws Exception {
        var runtime = new Runtime();
        try (var core = core(runtime)) {
            Files.writeString(root.resolve("World.kt"), original.replace("1f", "42f"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.value.get(0) != 42 && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(42f, runtime.value.get(0)); assertEquals(0, core.undoCount());
        }
    }
}
