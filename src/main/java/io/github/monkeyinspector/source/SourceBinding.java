package io.github.monkeyinspector.source;

import io.github.monkeyinspector.edit.*;
import java.nio.file.Path;
import java.util.Map;

public record SourceBinding(String id, Path sourceFile, int startLine, int endLine,
                            int startOffset, int endOffset, SourceRevision revision,
                            String code, Map<TransformProperty, Literal> literals) {
    public SourceBinding { literals = Map.copyOf(literals); }
    public record Literal(TransformValue value, java.util.List<Span> spans) {
        public Literal { spans = java.util.List.copyOf(spans); }
    }
    public record Span(int start, int end) {}
}
