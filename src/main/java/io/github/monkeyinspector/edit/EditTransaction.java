package io.github.monkeyinspector.edit;

import io.github.monkeyinspector.source.SourcePatcher;
import java.time.Instant;

public record EditTransaction(String editableId, TransformProperty property,
                              TransformValue runtimeBefore, TransformValue runtimeAfter,
                              SourcePatcher.Change source, Instant timestamp) {
    public enum Status { SUCCESS, RUNTIME_ONLY, SOURCE_CONFLICT, SOURCE_WRITE_FAILED,
        INVALID_BINDING, COMPUTED_SOURCE, CANCELLED, RUNTIME_FAILED }
    public record Result(Status status, String sessionId, String message) {}
}
