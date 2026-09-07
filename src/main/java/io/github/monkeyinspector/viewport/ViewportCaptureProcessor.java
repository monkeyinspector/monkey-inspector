package io.github.monkeyinspector.viewport;

import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image.Format;
import com.jme3.util.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Framebuffer copy on render thread; conversion and JPEG compression on a single daemon. */
public final class ViewportCaptureProcessor implements SceneProcessor {
    private final ViewportConfig config;
    private final FrameHub hub;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService encoder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "monkey-inspector-jpeg"); t.setDaemon(true); return t;
    });
    private RenderManager manager;
    private ViewPort viewport;
    private ByteBuffer pixels;
    private long last;
    private volatile String error = "";
    public ViewportCaptureProcessor(ViewportConfig config, FrameHub hub) { this.config = config; this.hub = hub; }
    public String error() { return error; }
    @Override public void initialize(RenderManager manager, ViewPort viewport) { this.manager = manager; this.viewport = viewport; }
    @Override public boolean isInitialized() { return manager != null; }
    @Override public void reshape(ViewPort viewport, int width, int height) { }
    @Override public void preFrame(float tpf) { }
    @Override public void postQueue(RenderQueue queue) { }
    @Override public void setProfiler(AppProfiler profiler) { }
    @Override public void postFrame(FrameBuffer output) {
        long now = System.nanoTime();
        if (!config.enabled() || !hub.hasViewers() || now - last < 1_000_000_000L / config.framesPerSecond() || !busy.compareAndSet(false, true)) return;
        last = now;
        int width = output == null ? viewport.getCamera().getWidth() : output.getWidth();
        int height = output == null ? viewport.getCamera().getHeight() : output.getHeight();
        // Bound readback memory even if a render target is unexpectedly enormous.
        if (width <= 0 || height <= 0 || (long)width * height > 16_777_216) { busy.set(false); return; }
        try {
            int size = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            if (pixels == null || pixels.capacity() != size) pixels = BufferUtils.createByteBuffer(size);
            pixels.clear();
            manager.getRenderer().readFrameBufferWithFormat(output, pixels, Format.RGBA8);
            ByteBuffer captured = pixels;
            encoder.execute(() -> {
                try { encode(captured, width, height); error = ""; }
                catch (Exception e) { error = "Viewport encode failed: " + e.getMessage(); }
                finally { busy.set(false); }
            });
        } catch (Exception e) { error = "Viewport capture failed: " + e.getMessage(); busy.set(false); }
    }
    private void encode(ByteBuffer data, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] rgb = ((java.awt.image.DataBufferInt)image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int offset = ((height - y - 1) * width + x) * 4;
            rgb[y * width + x] = (data.get(offset) & 255) << 16 | (data.get(offset + 1) & 255) << 8 | data.get(offset + 2) & 255;
        }
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var bytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(config.jpegQuality());
            writer.write(null, new IIOImage(image, null, null), parameters);
            output.flush(); hub.publish(bytes.toByteArray(), width, height);
        } finally { writer.dispose(); }
    }
    @Override public void cleanup() { manager = null; encoder.shutdownNow(); hub.close(); }
}
