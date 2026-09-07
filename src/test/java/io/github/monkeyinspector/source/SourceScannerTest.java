package io.github.monkeyinspector.source;

import io.github.monkeyinspector.edit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SourceScannerTest {
    @TempDir Path root;
    Path write(String code) throws IOException { return Files.writeString(root.resolve("World.kt"), code); }
    SourceBinding binding(String code) throws IOException { write(code); return new SourceScanner(root).scan().get("school.main"); }
    String block(String code) { return "// @mi-bind school.main\n" + code + "\n// @mi-end\n"; }

    @Test void parsesMultilineVectorAndUniformScale() throws Exception {
        var b = binding("package demo\n" + block("school.setLocalTranslation(\n 151f,\n 0f,\n -72f\n)\nschool.setLocalScale(1f)"));
        assertEquals(2, b.startLine()); assertEquals(9, b.endLine());
        assertEquals(TransformValue.of(151,0,-72), b.literals().get(TransformProperty.localTranslation).value());
        assertEquals(TransformValue.of(1,1,1), b.literals().get(TransformProperty.localScale).value());
    }
    @Test void patchesOnlyBindingAndPreservesFormattingAndCrLf() throws Exception {
        String before = "other.setLocalTranslation(151f, 0f, -72f)\r\n" + block("school.setLocalTranslation(\n    151f,\n    0f,\n    -72f\n)").replace("\n", "\r\n");
        var b = binding(before);
        var change = new SourcePatcher(new SourceScanner(root)).patch(b, TransformProperty.localTranslation, TransformValue.of(200,0,-72), b.revision().hash());
        assertEquals(before, change.before());
        assertTrue(change.after().startsWith("other.setLocalTranslation(151f, 0f, -72f)\r\n"));
        assertTrue(change.after().contains("\r\n    200.0f,\r\n"));
        assertEquals(change.after(), Files.readString(b.sourceFile()));
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }
    @Test void expandsUniformScale() throws Exception {
        var b = binding(block("school.setLocalScale(1f)"));
        var change = new SourcePatcher(new SourceScanner(root)).patch(b, TransformProperty.localScale, TransformValue.of(1,2,3), b.revision().hash());
        assertTrue(change.after().contains("setLocalScale(1.0f, 2.0f, 3.0f)"));
    }
    @Test void parsesJavaConstructorsAndQuaternion() throws Exception {
        var b = binding(block("school.setLocalTranslation(new Vector3f(1.0f, -2e1f, .5f));\nschool.setLocalRotation(new Quaternion(0f, 0f, 0f, 1f));"));
        assertEquals(TransformValue.of(1,-20,.5f), b.literals().get(TransformProperty.localTranslation).value());
        assertEquals(TransformValue.of(0,0,0,1), b.literals().get(TransformProperty.localRotation).value());
    }
    @Test void computedExpressionsArePreserved() throws Exception {
        String code = block("school.setLocalTranslation(mapCenter.x + offset, terrain.getHeight(x,z), spawnZ)\nschool.setLocalScale(1f)");
        var b = binding(code);
        assertFalse(b.literals().containsKey(TransformProperty.localTranslation));
        assertTrue(b.literals().containsKey(TransformProperty.localScale));
        assertThrows(IllegalArgumentException.class, () -> new SourcePatcher(new SourceScanner(root)).patch(b, TransformProperty.localTranslation, TransformValue.of(1,2,3), b.revision().hash()));
        assertEquals(code, Files.readString(b.sourceFile()));
    }
    @Test void duplicateIdsFailClosed() throws Exception {
        write(block("school.setLocalScale(1f)"));
        Files.writeString(root.resolve("Other.java"), block("school.setLocalScale(2f)"));
        assertThrows(IOException.class, () -> new SourceScanner(root).scan());
    }
    @Test void malformedMarkersFailClosed() throws Exception {
        for (String code : new String[]{"// @mi-bind school.main\n", "// @mi-end\n", "// @mi-bind school.main\n// @mi-bind x\n// @mi-end\n"}) {
            write(code); assertThrows(IOException.class, () -> new SourceScanner(root).scan());
        }
    }
    @Test void ignoresMarkersInCommentsAndStrings() throws Exception {
        write("/*\n// @mi-bind fake\n// @mi-end\n*/\nval example = \"\"\"\n// @mi-bind fake\n// @mi-end\n\"\"\"\n" + block("school.setLocalScale(1f)"));
        assertEquals(1, new SourceScanner(root).scan().size());
    }
    @Test void ambiguousCodeIsLocked() throws Exception {
        for (String code : new String[]{"if (flag) school.setLocalScale(1f)", "school.setLocalScale(1f)\nother.setLocalTranslation(1f,2f,3f)", "school.setLocalScale(1f)\nschool.setLocalScale(2f)", "school.setLocalScale(\"1\")"})
            assertTrue(binding(block(code)).literals().isEmpty(), code);
    }
    @Test void rejectsRevisionConflictWithoutWriting() throws Exception {
        var b = binding(block("school.setLocalScale(1f)"));
        String external = Files.readString(b.sourceFile()) + "// external\n";
        Files.writeString(b.sourceFile(), external);
        assertThrows(SourceConflictException.class, () -> new SourcePatcher(new SourceScanner(root)).patch(b, TransformProperty.localScale, TransformValue.of(2,2,2), b.revision().hash()));
        assertEquals(external, Files.readString(b.sourceFile()));
    }
    @Test void rejectsPathsOutsideRoot() throws Exception {
        Path allowed = Files.createDirectory(root.resolve("allowed"));
        Path outside = write(block("school.setLocalScale(1f)"));
        var scanner = new SourceScanner(allowed);
        assertThrows(IOException.class, () -> scanner.validate(allowed.resolve("../World.kt")));
        assertThrows(IOException.class, () -> new SourcePatcher(scanner).replace(outside, Files.readString(outside), "bad"));
    }
}
