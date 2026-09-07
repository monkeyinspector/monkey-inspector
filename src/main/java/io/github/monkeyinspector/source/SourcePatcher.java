package io.github.monkeyinspector.source;

import io.github.monkeyinspector.edit.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;

/** All calls serialized by the source worker. Revisions are checked again immediately before replace. */
public final class SourcePatcher {
    private final SourceScanner scanner;
    public SourcePatcher(SourceScanner scanner) { this.scanner = scanner; }
    public record Change(java.nio.file.Path file, String before, String after) {}

    public Change patch(SourceBinding binding, TransformProperty property, TransformValue value,
                        String expectedRevision) throws IOException {
        value.validate(property);
        Path file = scanner.validate(binding.sourceFile());
        String before = Files.readString(file);
        if (!SourceRevision.of(before).hash().equals(expectedRevision) || !binding.revision().hash().equals(expectedRevision))
            throw new SourceConflictException();
        var literal = binding.literals().get(property);
        if (literal == null) throw new IllegalArgumentException("Computed source: source editing locked");
        StringBuilder block = new StringBuilder(binding.code());
        var spans = literal.spans();
        if (spans.size() == 1) {
            var span = spans.get(0);
            block.replace(span.start(), span.end(), value.values().stream().map(v -> Float.toString(v) + "f").collect(Collectors.joining(", ")));
        } else {
            for (int i = spans.size() - 1; i >= 0; i--) {
                var span = spans.get(i);
                block.replace(span.start(), span.end(), Float.toString(value.get(i)) + "f");
            }
        }
        String after = before.substring(0, binding.startOffset()) + block + before.substring(binding.endOffset());
        replace(file, before, after);
        return new Change(file, before, after);
    }
    public void replace(Path path, String expected, String replacement) throws IOException {
        Path file = scanner.validate(path);
        if (!Files.readString(file).equals(expected)) throw new SourceConflictException();
        Path temp = Files.createTempFile(file.getParent(), ".mi-", ".tmp");
        try {
            Files.writeString(temp, replacement, StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file)); }
            catch (UnsupportedOperationException ignored) { }
            scanner.validate(file);
            if (!Files.readString(file).equals(expected)) throw new SourceConflictException();
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) {
                // Same-directory complete-file replacement; never truncate the destination.
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
