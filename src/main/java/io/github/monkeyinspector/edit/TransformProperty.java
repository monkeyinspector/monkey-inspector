package io.github.monkeyinspector.edit;

public enum TransformProperty {
    localTranslation("setLocalTranslation", 3),
    localRotation("setLocalRotation", 4),
    localScale("setLocalScale", 3);

    public final String setter;
    public final int components;
    TransformProperty(String setter, int components) {
        this.setter = setter;
        this.components = components;
    }
}
