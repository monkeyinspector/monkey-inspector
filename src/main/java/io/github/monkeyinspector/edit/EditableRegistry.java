package io.github.monkeyinspector.edit;

import com.jme3.scene.Spatial;
import java.util.*;

/** Owned by the application thread, including registration and parent lookup. */
public final class EditableRegistry {
    public static final String USER_DATA_KEY = "monkeyInspector.editableId";
    private final Map<String, EditableHandle> handles = new LinkedHashMap<>();

    public EditableHandle register(String id, Spatial spatial) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]{1,128}"))
            throw new IllegalArgumentException("Invalid editable id");
        Objects.requireNonNull(spatial);
        if (handles.containsKey(id) && handles.get(id).spatial() != spatial)
            throw new IllegalArgumentException("Duplicate editable id: " + id);
        for (var handle : handles.values())
            if (handle.spatial() == spatial && !handle.id().equals(id))
                throw new IllegalArgumentException("Spatial already registered");
        var handle = new EditableHandle(id, spatial);
        handles.put(id, handle);
        spatial.setUserData(USER_DATA_KEY, id);
        return handle;
    }
    public EditableHandle get(String id) {
        var handle = handles.get(id);
        if (handle == null) throw new IllegalArgumentException("Unknown editable: " + id);
        return handle;
    }
    public EditableHandle findParent(Spatial spatial) {
        for (Spatial current = spatial; current != null; current = current.getParent()) {
            String id = current.getUserData(USER_DATA_KEY);
            var handle = handles.get(id);
            if (handle != null && handle.spatial() == current) return handle;
        }
        return null;
    }
    public Collection<EditableHandle> handles() { return List.copyOf(handles.values()); }
}
