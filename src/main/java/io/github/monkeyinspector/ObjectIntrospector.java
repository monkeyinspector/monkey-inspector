package io.github.monkeyinspector;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

final class ObjectIntrospector {

    private ObjectIntrospector() {}

    static void writeFields(
            JsonWriter j,
            Object object,
            int maxFields
    ) {
        j.arrayStart();

        if (object == null || maxFields <= 0) {
            j.arrayEnd();
            return;
        }

        int count = 0;

        for (
                Class<?> c = object.getClass();
                c != null
                        && c != Object.class
                        && count < maxFields;
                c = c.getSuperclass()
        ) {
            if (isPlatformClass(c))
                break;

            for (Field field : c.getDeclaredFields()) {
                if (count >= maxFields)
                    break;

                int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers))
                    continue;

                if (field.isSynthetic())
                    continue;

                if (field.getName().contains("$delegate"))
                    continue;

                j.objectStart();

                j.name("name")
                        .value(field.getName());

                j.name("owner")
                        .value(c.getName());

                j.name("type")
                        .value(field.getType().getTypeName());

                j.name("final")
                        .value(Modifier.isFinal(modifiers));

                try {
                    if (
                            !field.canAccess(object)
                                    && !field.trySetAccessible()
                    ) {
                        j.name("value")
                                .value("<inaccessible>");

                        j.name("accessible")
                                .value(false);
                    } else {
                        Object value = field.get(object);

                        j.name("value")
                                .value(preview(value));

                        j.name("accessible")
                                .value(true);
                    }
                } catch (Throwable t) {
                    j.name("value")
                            .value(
                                    "<"
                                            + t.getClass()
                                            .getSimpleName()
                                            + ">"
                            );

                    j.name("accessible")
                            .value(false);
                }

                j.objectEnd();

                count++;
            }
        }

        j.arrayEnd();
    }

    static boolean isPlatformClass(Class<?> type) {
        String n = type.getName();

        return n.startsWith("java.")
                || n.startsWith("javax.")
                || n.startsWith("jdk.")
                || n.startsWith("sun.")
                || n.startsWith("kotlin.")
                || n.startsWith("com.jme3.");
    }

    static String preview(Object value) {
        if (value == null)
            return "null";

        Class<?> type = value.getClass();

        if (
                value instanceof CharSequence
                        || value instanceof Number
                        || value instanceof Boolean
                        || value instanceof Character
                        || value instanceof Enum<?>
        ) {
            return truncate(
                    String.valueOf(value),
                    180
            );
        }

        if (type.isArray()) {
            return type.getComponentType().getTypeName()
                    + "["
                    + Array.getLength(value)
                    + "]";
        }

        if (value instanceof Collection<?> collection) {
            return type.getSimpleName()
                    + "(size="
                    + collection.size()
                    + ")";
        }

        if (value instanceof Map<?, ?> map) {
            return type.getSimpleName()
                    + "(size="
                    + map.size()
                    + ")";
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(o -> "Optional[" + preview(o) + "]").orElse("Optional.empty");
        }

        if (type.getName().startsWith("com.jme3.math.")) {
            try {
                return truncate(
                        String.valueOf(value),
                        180
                );
            } catch (Throwable ignored) {}
        }

        return type.getName()
                + "@"
                + Integer.toHexString(
                System.identityHashCode(value)
        );
    }

    private static String truncate(
            String value,
            int max
    ) {
        if (value == null || value.length() <= max)
            return value;

        return value.substring(
                0,
                max - 1
        ) + "…";
    }
}