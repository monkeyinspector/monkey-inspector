package io.github.monkeyinspector.edit;

/** Implementations marshal every read and write to their runtime's owning thread. */
public interface RuntimeEditor {
    TransformValue read(String id, TransformProperty property) throws Exception;
    void write(String id, TransformProperty property, TransformValue value) throws Exception;
}
