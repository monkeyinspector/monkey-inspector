package io.github.monkeyinspector.viewport;

import com.jme3.math.Vector3f;

/** Owned by application thread; vectors are private to the drag controller. */
record DragSession(String editableId, Vector3f origin, Vector3f normal, Vector3f initialHit) {}
