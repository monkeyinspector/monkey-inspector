package io.github.monkeyinspector.edit;

import java.time.Instant;

public record EditSession(String id, String editableId, TransformProperty property, EditMode mode,
                          TransformValue before, String expectedRevision, Instant started) {}
