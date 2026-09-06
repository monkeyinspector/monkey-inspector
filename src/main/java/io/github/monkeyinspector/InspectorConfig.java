package io.github.monkeyinspector;

public record InspectorConfig(
        String host,
        int port,
        float snapshotIntervalSeconds,
        int maxSceneNodes,
        int maxFieldsPerObject,
        boolean profileEngine
) {
    public InspectorConfig {
        if (host == null || host.isBlank())
            throw new IllegalArgumentException("host must not be blank");

        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("port must be 1..65535");

        if (!(snapshotIntervalSeconds > 0f))
            throw new IllegalArgumentException(
                    "snapshotIntervalSeconds must be > 0"
            );

        if (maxSceneNodes < 1)
            throw new IllegalArgumentException(
                    "maxSceneNodes must be > 0"
            );

        if (maxFieldsPerObject < 0)
            throw new IllegalArgumentException(
                    "maxFieldsPerObject must be >= 0"
            );
    }

    public static InspectorConfig defaults() {
        return new InspectorConfig(
                "127.0.0.1",
                7331,
                0.25f,
                20_000,
                48,
                true
        );
    }

    public InspectorConfig withPort(int value) {
        return new InspectorConfig(
                host,
                value,
                snapshotIntervalSeconds,
                maxSceneNodes,
                maxFieldsPerObject,
                profileEngine
        );
    }

    public InspectorConfig withSnapshotInterval(float value) {
        return new InspectorConfig(
                host,
                port,
                value,
                maxSceneNodes,
                maxFieldsPerObject,
                profileEngine
        );
    }

    public InspectorConfig withMaxSceneNodes(int value) {
        return new InspectorConfig(
                host,
                port,
                snapshotIntervalSeconds,
                value,
                maxFieldsPerObject,
                profileEngine
        );
    }

    public InspectorConfig withMaxFieldsPerObject(int value) {
        return new InspectorConfig(
                host,
                port,
                snapshotIntervalSeconds,
                maxSceneNodes,
                value,
                profileEngine
        );
    }

    public InspectorConfig withEngineProfiling(boolean value) {
        return new InspectorConfig(
                host,
                port,
                snapshotIntervalSeconds,
                maxSceneNodes,
                maxFieldsPerObject,
                value
        );
    }
}