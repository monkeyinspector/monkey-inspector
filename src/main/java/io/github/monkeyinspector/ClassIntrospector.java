package io.github.monkeyinspector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ClassIntrospector {
    private ClassIntrospector() {}

    static void writeClassInfo(JsonWriter j, Class<?> type) {
        j.objectStart();
        j.name("name").value(type.getName());
        j.name("simpleName").value(type.getSimpleName());
        j.name("hierarchy").arrayStart();
        Class<?> c = type;
        while (c != null) {
            j.value(c.getName());
            c = c.getSuperclass();
        }
        j.arrayEnd();

        j.name("interfaces").arrayStart();
        for (Class<?> i : allInterfaces(type)) j.value(i.getName());
        j.arrayEnd();
        j.objectEnd();
    }

    private static List<Class<?>> allInterfaces(Class<?> type) {
        Set<Class<?>> set = new LinkedHashSet<>();
        Class<?> c = type;
        while (c != null) {
            collect(c, set);
            c = c.getSuperclass();
        }
        return new ArrayList<>(set);
    }

    private static void collect(Class<?> c, Set<Class<?>> set) {
        for (Class<?> i : c.getInterfaces()) {
            if (set.add(i)) collect(i, set);
        }
    }
}
