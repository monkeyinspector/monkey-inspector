package io.github.monkeyinspector.viewport;

import com.jme3.collision.CollisionResults;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import io.github.monkeyinspector.edit.*;

/** Application-thread-only camera and collision operations. */
public final class PickingService {
    private final Camera camera;
    private final Node root;
    private final EditableRegistry registry;
    public PickingService(Camera camera, Node root, EditableRegistry registry) { this.camera = camera; this.root = root; this.registry = registry; }
    public Ray ray(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("Coordinates must be in 0..1");
        var point = new Vector2f(x * camera.getWidth(), (1 - y) * camera.getHeight());
        var near = camera.getWorldCoordinates(point, 0);
        var far = camera.getWorldCoordinates(point, 1);
        return new Ray(near, far.subtract(near).normalizeLocal());
    }
    public EditableHandle pick(float x, float y) {
        var collisions = new CollisionResults();
        root.collideWith(ray(x, y), collisions);
        // The nearest visible collision wins; do not select through an uneditable occluder.
        return collisions.size() == 0 ? null : registry.findParent(collisions.getClosestCollision().getGeometry());
    }
}
