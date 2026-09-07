package io.github.monkeyinspector.viewport;

import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import io.github.monkeyinspector.edit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewportTest {
    Camera camera() {
        Camera camera = new Camera(800,600);
        camera.setFrustumPerspective(60, 800f/600f, .1f, 100);
        camera.setLocation(new Vector3f(0,0,10)); camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y); camera.update();
        return camera;
    }
    @Test void registryFindsEditableAncestorAndRejectsDuplicates() {
        var registry = new EditableRegistry(); var root = new Node(); var child = new Node(); var geometry = new Geometry();
        root.attachChild(child); child.attachChild(geometry);
        var handle = registry.register("school", root);
        assertSame(handle, registry.findParent(geometry));
        assertThrows(IllegalArgumentException.class, () -> registry.register("school", child));
        assertThrows(IllegalArgumentException.class, () -> registry.register("other", root));
        assertThrows(IllegalArgumentException.class, () -> registry.register("../bad", child));
    }
    @Test void picksClosestCollisionAndFlipsBrowserYAxis() {
        var registry = new EditableRegistry(); var root = new Node(); var school = new Node();
        school.attachChild(new Geometry("box", new Box(1,1,1))); root.attachChild(school); registry.register("school", school);
        root.updateGeometricState();
        var picking = new PickingService(camera(), root, registry);
        assertEquals("school", picking.pick(.5f,.5f).id());
        assertTrue(picking.ray(.5f,0).getDirection().y > 0);
        assertThrows(IllegalArgumentException.class, () -> picking.ray(Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> picking.ray(2, 0));
    }
    @Test void draggingConvertsWorldDeltaThroughRotatedScaledParent() {
        var camera = camera(); var registry = new EditableRegistry(); var root = new Node(); var parent = new Node(); var object = new Node();
        root.attachChild(parent); parent.attachChild(object);
        parent.setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Z));
        parent.setLocalScale(2,3,4); root.updateGeometricState(); registry.register("object", object);
        var picking = new PickingService(camera, root, registry); var drag = new DragController(camera,picking,registry);
        drag.begin("object",.5f,.5f); var value = drag.preview(.6f,.5f);
        assertEquals(0, value.get(0), 1e-5); assertTrue(value.get(1) < 0); assertEquals(0, value.get(2), 1e-5);
        var world = parent.localToWorld(new Vector3f(value.get(0),value.get(1),value.get(2)), null);
        var projected = camera.getScreenCoordinates(world);
        assertEquals(.6f, projected.x / 800, 1e-5); assertEquals(.5f, projected.y / 600, 1e-5);
    }
    @Test void frameHubPublishesLatestAndClosesWaiters() throws Exception {
        var hub = new FrameHub(); byte[] bytes = {1,2}; hub.publish(bytes,800,600); bytes[0] = 9;
        assertEquals(1, hub.await(0).jpeg()[0]);
        hub.publish(new byte[]{3},800,600); assertEquals(2,hub.await(0).sequence());
        for (int i=0;i<4;i++) assertTrue(hub.subscribe()); assertFalse(hub.subscribe());
        hub.close(); assertNull(hub.await(2)); assertFalse(hub.hasViewers());
    }
}
