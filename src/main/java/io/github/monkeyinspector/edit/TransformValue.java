package io.github.monkeyinspector.edit;

import java.util.List;

/** Immutable transport value; rotations use quaternion x/y/z/w. */
public record TransformValue(List<Float> values) {
    public TransformValue {
        values = List.copyOf(values);
        if (values.stream().anyMatch(v -> !Float.isFinite(v)))
            throw new IllegalArgumentException("Transform must be finite");
    }
    public static TransformValue of(float... values) {
        var list = new java.util.ArrayList<Float>();
        for (float value : values) list.add(value);
        return new TransformValue(list);
    }
    public float get(int i) { return values.get(i); }
    public void validate(TransformProperty property) {
        if (values.size() != property.components)
            throw new IllegalArgumentException("Invalid component count for " + property);
        if (property == TransformProperty.localRotation) {
            double norm = values.stream().mapToDouble(v -> (double)v * v).sum();
            if (Math.abs(norm - 1) > 0.001)
                throw new IllegalArgumentException("Rotation must be a unit quaternion (x,y,z,w)");
        }
    }
}
