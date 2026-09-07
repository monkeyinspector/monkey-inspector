package io.github.monkeyinspector.viewport;

import io.github.monkeyinspector.edit.*;

/** Called on EditCore's worker. Camera work is enqueued; transactions use the shared core. */
public final class ViewportEditor {
    private final JmeRuntimeEditor runtime;
    private final PickingService picking;
    private final DragController drag;
    private String selected;
    public ViewportEditor(JmeRuntimeEditor runtime, PickingService picking, DragController drag) {
        this.runtime = runtime; this.picking = picking; this.drag = drag;
    }
    public String pick(float x, float y) throws Exception {
        selected = runtime.call(() -> { var h = picking.pick(x, y); return h == null ? null : h.id(); });
        return selected;
    }
    public void select(String id) { selected = id; }
    public String selected() { return selected; }
    public void begin(String id, float x, float y) throws Exception { runtime.call(() -> { drag.begin(id, x, y); return null; }); selected = id; }
    public TransformValue position(float x, float y) throws Exception { return runtime.call(() -> drag.preview(x, y)); }
    public void end() throws Exception { runtime.call(() -> { drag.end(); return null; }); }
}
