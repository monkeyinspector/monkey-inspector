package io.github.monkeyinspector.viewport;

public record ViewportConfig(boolean enabled, int framesPerSecond, float jpegQuality) {
    public ViewportConfig {
        if (framesPerSecond < 1 || framesPerSecond > 30) throw new IllegalArgumentException("FPS must be 1..30");
        if (!(jpegQuality > 0 && jpegQuality <= 1)) throw new IllegalArgumentException("JPEG quality must be 0..1");
    }
    public static ViewportConfig defaults() { return new ViewportConfig(true, 12, 0.75f); }
}
