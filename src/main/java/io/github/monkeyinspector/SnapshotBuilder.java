package io.github.monkeyinspector;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class SnapshotBuilder {
    private final Application app;
    private final List<Object> watchedObjects;
    private final int maxNodes;
    private int visited;

    SnapshotBuilder(Application app, CopyOnWriteArrayList<Object> watchedObjects, int maxNodes) {
        this.app = app;
        this.watchedObjects = watchedObjects;
        this.maxNodes = maxNodes;
    }

    String build() {
        visited = 0;
        JsonWriter j = new JsonWriter();
        j.objectStart();
        j.name("timestampMillis").value(System.currentTimeMillis());
        j.name("applicationClass"); ClassIntrospector.writeClassInfo(j, app.getClass());

        j.name("sceneRoots").arrayStart();
        if (app instanceof SimpleApplication) {
            SimpleApplication simple = (SimpleApplication) app;
            writeSpatial(j, simple.getRootNode());
            writeSpatial(j, simple.getGuiNode());
        }
        j.arrayEnd();

        j.name("appStates").arrayStart();
        for (AppState state : readAppStates(app.getStateManager())) writeObject(j, state, state.isEnabled());
        j.arrayEnd();

        j.name("watchedObjects").arrayStart();
        for (Object object : watchedObjects) {
            if (object != null) writeObject(j, object, true);
        }
        j.arrayEnd();

        j.name("trace"); InspectorTrace.writeJson(j);
        j.name("truncated").value(visited >= maxNodes);
        j.objectEnd();
        return j.toString();
    }

    private void writeSpatial(JsonWriter j, Spatial spatial) {
        if (spatial == null || visited >= maxNodes) return;
        visited++;

        j.objectStart();
        j.name("id").value(id(spatial));
        j.name("name").value(spatial.getName() == null ? "<unnamed>" : spatial.getName());
        j.name("kind").value(spatial instanceof Geometry ? "Geometry" : spatial instanceof Node ? "Node" : "Spatial");
        j.name("classInfo"); ClassIntrospector.writeClassInfo(j, spatial.getClass());
        j.name("localTranslation").value(String.valueOf(spatial.getLocalTranslation()));
        j.name("worldTranslation").value(String.valueOf(spatial.getWorldTranslation()));
        j.name("localRotation").value(String.valueOf(spatial.getLocalRotation()));
        j.name("localScale").value(String.valueOf(spatial.getLocalScale()));
        j.name("cullHint").value(String.valueOf(spatial.getCullHint()));
        j.name("queueBucket").value(String.valueOf(spatial.getQueueBucket()));
        j.name("triangles").value(spatial.getTriangleCount());
        j.name("vertices").value(spatial.getVertexCount());

        j.name("controls").arrayStart();
        for (int i = 0; i < spatial.getNumControls(); i++) {
            Control control = spatial.getControl(i);
            j.objectStart();
            j.name("id").value(id(control));
            j.name("classInfo"); ClassIntrospector.writeClassInfo(j, control.getClass());
            j.objectEnd();
        }
        j.arrayEnd();

        j.name("children").arrayStart();
        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                if (visited >= maxNodes) break;
                writeSpatial(j, child);
            }
        }
        j.arrayEnd();
        j.objectEnd();
    }

    private static void writeObject(JsonWriter j, Object object, boolean enabled) {
        j.objectStart();
        j.name("id").value(id(object));
        j.name("enabled").value(enabled);
        j.name("classInfo"); ClassIntrospector.writeClassInfo(j, object.getClass());
        j.name("toString").value(safeToString(object));
        j.objectEnd();
    }

    private static AppState[] readAppStates(AppStateManager manager) {
        try {
            Method m = AppStateManager.class.getDeclaredMethod("getStates");
            m.setAccessible(true);
            return (AppState[]) m.invoke(manager);
        } catch (Throwable ignored) {
            return new AppState[0];
        }
    }

    private static String id(Object object) {
        return object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    private static String safeToString(Object object) {
        try { return String.valueOf(object); }
        catch (Throwable t) { return "<toString failed: " + t.getClass().getSimpleName() + ">"; }
    }
}
