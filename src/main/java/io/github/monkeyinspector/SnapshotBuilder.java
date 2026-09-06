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
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

final class SnapshotBuilder {

    private final Application app;

    private final List<WatchedObject>
            watchedObjects;

    private final int maxNodes;

    private final int maxFieldsPerObject;

    private final InspectorAppProfiler
            engineProfiler;

    private int visited;

    SnapshotBuilder(
            Application app,
            CopyOnWriteArrayList<WatchedObject> watchedObjects,
            int maxNodes,
            int maxFieldsPerObject,
            InspectorAppProfiler engineProfiler
    ) {
        this.app = app;
        this.watchedObjects = watchedObjects;
        this.maxNodes = maxNodes;
        this.maxFieldsPerObject =
                maxFieldsPerObject;
        this.engineProfiler =
                engineProfiler;
    }

    String build() {
        visited = 0;

        ArchitectureCollector architecture =
                new ArchitectureCollector();

        architecture.observe(
                app.getClass()
        );

        JsonWriter j =
                new JsonWriter();

        j.objectStart();

        j.name("version")
                .value("0.2.0");

        j.name("timestampMillis")
                .value(
                        System.currentTimeMillis()
                );

        j.name("applicationClass");

        ClassIntrospector.writeClassInfo(
                j,
                app.getClass()
        );

        j.name("sceneRoots")
                .arrayStart();

        if (
                app
                        instanceof
                        SimpleApplication simple
        ) {
            writeSpatial(
                    j,
                    simple.getRootNode(),
                    architecture
            );

            writeSpatial(
                    j,
                    simple.getGuiNode(),
                    architecture
            );
        }

        j.arrayEnd();

        j.name("appStates")
                .arrayStart();

        for (
                AppState state :
                readAppStates(
                        app.getStateManager()
                )
        ) {
            architecture.observe(
                    state.getClass()
            );

            writeAppState(
                    j,
                    state
            );
        }

        j.arrayEnd();

        j.name("watchedObjects")
                .arrayStart();

        for (
                WatchedObject watched :
                watchedObjects
        ) {
            Object object =
                    watched.object();

            if (object == null)
                continue;

            architecture.observe(
                    object.getClass()
            );

            writeObject(
                    j,
                    watched.name(),
                    object
            );
        }

        j.arrayEnd();

        j.name("architecture");

        architecture.writeJson(j);

        j.name("trace");

        InspectorTrace.writeJson(j);

        j.name("engineProfile");

        if (engineProfiler != null) {
            engineProfiler.writeJson(j);
        } else {
            j.raw(
                    "{\"enabled\":false}"
            );
        }

        j.name("stats")
                .objectStart();

        j.name("visitedSceneNodes")
                .value(visited);

        j.name("maxSceneNodes")
                .value(maxNodes);

        j.name("truncated")
                .value(
                        visited >= maxNodes
                );

        j.objectEnd();

        j.objectEnd();

        return j.toString();
    }

    private void writeSpatial(
            JsonWriter j,
            Spatial spatial,
            ArchitectureCollector architecture
    ) {
        if (
                spatial == null
                        || visited >= maxNodes
        ) {
            return;
        }

        visited++;

        architecture.observe(
                spatial.getClass()
        );

        j.objectStart();

        j.name("id")
                .value(id(spatial));

        j.name("name")
                .value(
                        spatial.getName() == null
                                ? "<unnamed>"
                                : spatial.getName()
                );

        j.name("kind")
                .value(
                        spatial instanceof Geometry
                                ? "Geometry"
                                : spatial instanceof Node
                                ? "Node"
                                : "Spatial"
                );

        j.name("classInfo");

        ClassIntrospector.writeClassInfo(
                j,
                spatial.getClass()
        );

        j.name("localTranslation")
                .value(
                        String.valueOf(
                                spatial
                                        .getLocalTranslation()
                        )
                );

        j.name("worldTranslation")
                .value(
                        String.valueOf(
                                spatial
                                        .getWorldTranslation()
                        )
                );

        j.name("localRotation")
                .value(
                        String.valueOf(
                                spatial
                                        .getLocalRotation()
                        )
                );

        j.name("localScale")
                .value(
                        String.valueOf(
                                spatial
                                        .getLocalScale()
                        )
                );

        j.name("cullHint")
                .value(
                        String.valueOf(
                                spatial.getCullHint()
                        )
                );

        j.name("queueBucket")
                .value(
                        String.valueOf(
                                spatial.getQueueBucket()
                        )
                );

        j.name("worldBound")
                .value(
                        spatial.getWorldBound()
                                == null
                                ? null
                                : String.valueOf(
                                spatial.getWorldBound()
                        )
                );

        if (
                spatial
                        instanceof Geometry geometry
        ) {
            j.name("triangles")
                    .value(
                            geometry
                                    .getTriangleCount()
                    );

            j.name("vertices")
                    .value(
                            geometry
                                    .getVertexCount()
                    );

            if (geometry.getMesh() != null) {
                j.name("meshMode")
                        .value(
                                String.valueOf(
                                        geometry
                                                .getMesh()
                                                .getMode()
                                )
                        );
            }
        }

        j.name("controls")
                .arrayStart();

        for (
                int i = 0;
                i < spatial.getNumControls();
                i++
        ) {
            Control control =
                    spatial.getControl(i);

            if (control == null)
                continue;

            architecture.observe(
                    control.getClass()
            );

            j.objectStart();

            j.name("id")
                    .value(id(control));

            j.name("name")
                    .value(
                            control
                                    .getClass()
                                    .getSimpleName()
                    );

            j.name("kind")
                    .value("Control");

            j.name("classInfo");

            ClassIntrospector.writeClassInfo(
                    j,
                    control.getClass()
            );

            j.name("fields");

            ObjectIntrospector.writeFields(
                    j,
                    control,
                    maxFieldsPerObject
            );

            j.objectEnd();
        }

        j.arrayEnd();

        j.name("children")
                .arrayStart();

        if (
                spatial
                        instanceof Node node
        ) {
            for (
                    Spatial child :
                    node.getChildren()
            ) {
                if (visited >= maxNodes)
                    break;

                writeSpatial(
                        j,
                        child,
                        architecture
                );
            }
        }

        j.arrayEnd();

        j.objectEnd();
    }

    private void writeAppState(
            JsonWriter j,
            AppState state
    ) {
        j.objectStart();

        j.name("id")
                .value(id(state));

        j.name("name")
                .value(
                        state.getId() == null
                                ? state
                                .getClass()
                                .getSimpleName()
                                : state.getId()
                );

        j.name("kind")
                .value("AppState");

        j.name("enabled")
                .value(
                        state.isEnabled()
                );

        j.name("initialized")
                .value(
                        state.isInitialized()
                );

        j.name("classInfo");

        ClassIntrospector.writeClassInfo(
                j,
                state.getClass()
        );

        j.name("fields");

        ObjectIntrospector.writeFields(
                j,
                state,
                maxFieldsPerObject
        );

        j.objectEnd();
    }

    private void writeObject(
            JsonWriter j,
            String name,
            Object object
    ) {
        j.objectStart();

        j.name("id")
                .value(id(object));

        j.name("name")
                .value(name);

        j.name("kind")
                .value("Object");

        j.name("classInfo");

        ClassIntrospector.writeClassInfo(
                j,
                object.getClass()
        );

        j.name("preview")
                .value(
                        ObjectIntrospector.preview(
                                object
                        )
                );

        j.name("fields");

        ObjectIntrospector.writeFields(
                j,
                object,
                maxFieldsPerObject
        );

        j.objectEnd();
    }

    private static AppState[] readAppStates(
            AppStateManager manager
    ) {
        List<AppState> result =
                new ArrayList<>();

        Set<AppState> seen =
                Collections.newSetFromMap(
                        new IdentityHashMap<>()
                );

        appendStates(
                manager,
                "getStates",
                result,
                seen
        );

        appendStates(
                manager,
                "getInitializing",
                result,
                seen
        );

        return result.toArray(
                AppState[]::new
        );
    }

    private static void appendStates(
            AppStateManager manager,
            String methodName,
            List<AppState> output,
            Set<AppState> seen
    ) {
        try {
            Method method =
                    AppStateManager.class
                            .getDeclaredMethod(
                                    methodName
                            );

            if (!method.canAccess(manager)) {
                method.setAccessible(true);
            }

            AppState[] states =
                    (AppState[])
                            method.invoke(manager);

            if (states == null)
                return;

            for (
                    AppState state :
                    states
            ) {
                if (
                        state != null
                                && seen.add(state)
                ) {
                    output.add(state);
                }
            }

        } catch (Throwable ignored) {}
    }

    private static String id(
            Object object
    ) {
        return object
                .getClass()
                .getName()
                + "@"
                + Integer.toHexString(
                System.identityHashCode(
                        object
                )
        );
    }
}