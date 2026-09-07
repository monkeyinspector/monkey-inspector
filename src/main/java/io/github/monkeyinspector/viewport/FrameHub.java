package io.github.monkeyinspector.viewport;

/** Latest-only publication: slow viewers never accumulate a queue. JPEG bytes are not exposed mutably. */
public final class FrameHub implements AutoCloseable {
    public record Frame(long sequence, byte[] jpeg, int width, int height) {
        public Frame { jpeg = jpeg.clone(); }
        @Override public byte[] jpeg() { return jpeg.clone(); }
    }
    private Frame latest;
    private boolean closed;
    private int viewers;
    public synchronized void publish(byte[] jpeg, int width, int height) {
        if (closed) return;
        latest = new Frame(latest == null ? 1 : latest.sequence() + 1, jpeg, width, height);
        notifyAll();
    }
    public synchronized Frame await(long after) throws InterruptedException {
        if (!closed && (latest == null || latest.sequence() <= after)) wait(1000);
        return closed || latest == null || latest.sequence() <= after ? null : latest;
    }
    public synchronized boolean subscribe() {
        if (closed || viewers >= 4) return false;
        viewers++; return true;
    }
    public synchronized void unsubscribe() { viewers = Math.max(0, viewers - 1); }
    public synchronized boolean hasViewers() { return viewers > 0 && !closed; }
    public synchronized boolean closed() { return closed; }
    @Override public synchronized void close() { closed = true; notifyAll(); }
}
