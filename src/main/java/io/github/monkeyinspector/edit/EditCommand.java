package io.github.monkeyinspector.edit;

public record EditCommand(String editableId, TransformProperty property, TransformValue value,
                          EditMode mode, EditPhase phase, String sessionId, String expectedSourceRevision) {
    public EditCommand {
        if (mode == null) mode = EditMode.BOTH;
        if (phase == null) phase = EditPhase.COMMIT;
    }
}
