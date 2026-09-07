package io.github.monkeyinspector.source;

import java.io.IOException;

public final class SourceConflictException extends IOException {
    public SourceConflictException() { super("Source changed externally. Reload before editing."); }
}
