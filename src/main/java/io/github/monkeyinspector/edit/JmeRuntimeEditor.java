package io.github.monkeyinspector.edit;

import com.jme3.app.Application;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public final class JmeRuntimeEditor implements RuntimeEditor {
    private final Application app;
    private final EditableRegistry registry;
    public JmeRuntimeEditor(Application app, EditableRegistry registry) {
        this.app = app; this.registry = registry;
    }
    public <T> T call(Callable<T> task) throws Exception {
        var future = app.enqueue(task);
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { future.cancel(false); throw e; }
    }
    @Override public TransformValue read(String id, TransformProperty property) throws Exception {
        return call(() -> {
            var spatial = registry.get(id).spatial();
            if (property == TransformProperty.localRotation) {
                var q = spatial.getLocalRotation();
                return TransformValue.of(q.getX(), q.getY(), q.getZ(), q.getW());
            }
            var v = property == TransformProperty.localTranslation
                    ? spatial.getLocalTranslation() : spatial.getLocalScale();
            return TransformValue.of(v.x, v.y, v.z);
        });
    }
    @Override public void write(String id, TransformProperty property, TransformValue value) throws Exception {
        value.validate(property);
        call(() -> {
            var spatial = registry.get(id).spatial();
            switch (property) {
                case localTranslation -> spatial.setLocalTranslation(new Vector3f(value.get(0), value.get(1), value.get(2)));
                case localScale -> spatial.setLocalScale(new Vector3f(value.get(0), value.get(1), value.get(2)));
                case localRotation -> spatial.setLocalRotation(new Quaternion(value.get(0), value.get(1), value.get(2), value.get(3)));
            }
            return null;
        });
    }
}
