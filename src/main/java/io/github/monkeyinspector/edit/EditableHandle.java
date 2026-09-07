package io.github.monkeyinspector.edit;

import com.jme3.scene.Spatial;

/** Spatial access is restricted to the application thread. */
public record EditableHandle(String id, Spatial spatial) {}
