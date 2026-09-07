package io.github.monkeyinspector.viewport;

import com.jme3.math.*;
import com.jme3.renderer.Camera;
import io.github.monkeyinspector.edit.*;

/** Only computes translation; all mutations go through EditCore. Application-thread confined. */
public final class DragController {
    private final Camera camera;
    private final PickingService picking;
    private final EditableRegistry registry;
    private DragSession session;
    public DragController(Camera camera, PickingService picking, EditableRegistry registry) { this.camera = camera; this.picking = picking; this.registry = registry; }
    public void begin(String id, float x, float y) {
        var origin = registry.get(id).spatial().getWorldTranslation().clone();
        var normal = camera.getDirection().clone();
        session = new DragSession(id, origin, normal, intersect(picking.ray(x, y), origin, normal));
    }
    public TransformValue preview(float x, float y) {
        if (session == null) throw new IllegalStateException("No drag session");
        var world = session.origin().add(intersect(picking.ray(x, y), session.origin(), session.normal()).subtract(session.initialHit()));
        var parent = registry.get(session.editableId()).spatial().getParent();
        var local = parent == null ? world : parent.worldToLocal(world, new Vector3f());
        return TransformValue.of(local.x, local.y, local.z);
    }
    private static Vector3f intersect(Ray ray, Vector3f origin, Vector3f normal) {
        float divisor = normal.dot(ray.getDirection());
        if (Math.abs(divisor) < 1e-6f) throw new IllegalArgumentException("Ray parallel to drag plane");
        float distance = normal.dot(origin.subtract(ray.getOrigin())) / divisor;
        if (distance < 0) throw new IllegalArgumentException("Drag plane behind camera");
        return ray.getOrigin().add(ray.getDirection().mult(distance));
    }
    public void end() { session = null; }
}
