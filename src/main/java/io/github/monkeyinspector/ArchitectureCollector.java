package io.github.monkeyinspector;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ArchitectureCollector {

    private final Set<Class<?>> observed =
            new LinkedHashSet<>();

    void observe(Class<?> type) {
        if (type != null)
            observed.add(type);
    }

    void writeJson(JsonWriter j) {
        Set<Class<?>> nodes =
                new LinkedHashSet<>();

        Set<Edge> edges =
                new LinkedHashSet<>();

        for (Class<?> type : observed) {
            addType(nodes, type);

            Class<?> parent =
                    type.getSuperclass();

            if (parent != null) {
                addType(nodes, parent);

                edges.add(
                        new Edge(
                                type.getName(),
                                parent.getName(),
                                "extends",
                                null
                        )
                );
            }

            for (Class<?> iface : type.getInterfaces()) {
                addType(nodes, iface);

                edges.add(
                        new Edge(
                                type.getName(),
                                iface.getName(),
                                "implements",
                                null
                        )
                );
            }

            if (!ObjectIntrospector.isPlatformClass(type)) {
                for (Field field : type.getDeclaredFields()) {
                    if (
                            Modifier.isStatic(
                                    field.getModifiers()
                            )
                    ) continue;

                    if (field.isSynthetic())
                        continue;

                    Class<?> dependency =
                            dependencyType(
                                    field.getType()
                            );

                    if (
                            dependency == null
                                    || dependency == type
                    ) continue;

                    addType(
                            nodes,
                            dependency
                    );

                    edges.add(
                            new Edge(
                                    type.getName(),
                                    dependency.getName(),
                                    "field",
                                    field.getName()
                            )
                    );
                }
            }
        }

        List<Class<?>> sortedNodes =
                new ArrayList<>(nodes);

        sortedNodes.sort(
                Comparator.comparing(
                        Class::getName
                )
        );

        List<Edge> sortedEdges =
                new ArrayList<>(edges);

        sortedEdges.sort(
                Comparator
                        .comparing(Edge::from)
                        .thenComparing(Edge::to)
                        .thenComparing(Edge::kind)
        );

        j.objectStart();

        j.name("nodes")
                .arrayStart();

        for (Class<?> type : sortedNodes) {
            j.objectStart();

            j.name("name")
                    .value(type.getName());

            j.name("simpleName")
                    .value(type.getSimpleName());

            j.name("packageName")
                    .value(type.getPackageName());

            j.name("kind")
                    .value(
                            type.isInterface()
                                    ? "interface"
                                    : type.isEnum()
                                    ? "enum"
                                    : type.isRecord()
                                    ? "record"
                                    : "class"
                    );

            j.name("observed")
                    .value(
                            observed.contains(type)
                    );

            j.objectEnd();
        }

        j.arrayEnd();

        j.name("edges")
                .arrayStart();

        for (Edge edge : sortedEdges) {
            j.objectStart();

            j.name("from")
                    .value(edge.from());

            j.name("to")
                    .value(edge.to());

            j.name("kind")
                    .value(edge.kind());

            if (edge.label() != null) {
                j.name("label")
                        .value(edge.label());
            }

            j.objectEnd();
        }

        j.arrayEnd();

        j.objectEnd();
    }

    private static void addType(
            Set<Class<?>> nodes,
            Class<?> type
    ) {
        if (
                type != null
                        && !type.isPrimitive()
                        && type != void.class
        ) {
            nodes.add(type);
        }
    }

    private static Class<?> dependencyType(
            Class<?> type
    ) {
        while (type.isArray()) {
            type = type.getComponentType();
        }

        if (
                type.isPrimitive()
                        || type == String.class
                        || type == Class.class
        ) {
            return null;
        }

        String name = type.getName();

        if (
                name.startsWith("java.")
                        || name.startsWith("javax.")
                        || name.startsWith("jdk.")
                        || name.startsWith("sun.")
        ) {
            return null;
        }

        return type;
    }

    private record Edge(
            String from,
            String to,
            String kind,
            String label
    ) {}
}