package io.github.monkeyinspector;

/**
 * Immutable configuration for an {@link InspectorState}.
 *
 * @param host address on which the inspector HTTP server listens
 * @param port TCP port used by the inspector HTTP server
 * @param snapshotIntervalSeconds interval between scene snapshots, in seconds
 * @param maxSceneNodes maximum number of scene nodes included in a snapshot
 * @param maxFieldsPerObject maximum number of reflected fields per object
 * @param profileEngine whether jMonkeyEngine profiling data is collected
 */
public record InspectorConfig(
        String host,
        int port,
        float snapshotIntervalSeconds,
        int maxSceneNodes,
        int maxFieldsPerObject,
        boolean profileEngine
) {
    /**
     * Validates and creates an inspector configuration.
     */
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

    /**
     * Returns the local-only default configuration.
     *
     * @return the default configuration
     */
    public static InspectorConfig defaults() {
        return new InspectorConfig(
                "127.0.0.1",
                InspectorState.DEFAULT_PORT,
                0.25f,
                20_000,
                48,
                true
        );
    }

    /**
     * Returns a copy with a different HTTP port.
     *
     * @param value port in the range 1 through 65535
     * @return the updated configuration
     */
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

    /**
     * Returns a copy with a different snapshot interval.
     *
     * @param value positive interval in seconds
     * @return the updated configuration
     */
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

    /**
     * Returns a copy with a different scene-node limit.
     *
     * @param value positive maximum node count
     * @return the updated configuration
     */
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

    /**
     * Returns a copy with a different reflected-field limit.
     *
     * @param value non-negative maximum field count
     * @return the updated configuration
     */
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

    /**
     * Returns a copy with engine profiling enabled or disabled.
     *
     * @param value whether engine profiling is enabled
     * @return the updated configuration
     */
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
