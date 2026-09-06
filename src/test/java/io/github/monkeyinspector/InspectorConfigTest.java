package io.github.monkeyinspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorConfigTest {

    @Test
    void defaultsAreLocalAndUsable() {
        InspectorConfig config =
                InspectorConfig.defaults();

        assertEquals("127.0.0.1", config.host());
        assertEquals(InspectorState.DEFAULT_PORT, config.port());
        assertTrue(config.snapshotIntervalSeconds() > 0f);
        assertTrue(config.maxSceneNodes() > 0);
    }

    @Test
    void copyMethodsOnlyChangeTheirOption() {
        InspectorConfig defaults =
                InspectorConfig.defaults();

        InspectorConfig changed =
                defaults
                        .withPort(8080)
                        .withSnapshotInterval(0.5f)
                        .withMaxSceneNodes(123)
                        .withMaxFieldsPerObject(12)
                        .withEngineProfiling(false);

        assertEquals("127.0.0.1", changed.host());
        assertEquals(8080, changed.port());
        assertEquals(0.5f, changed.snapshotIntervalSeconds());
        assertEquals(123, changed.maxSceneNodes());
        assertEquals(12, changed.maxFieldsPerObject());
        assertFalse(changed.profileEngine());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InspectorConfig("", 7331, 0.25f, 1, 0, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InspectorConfig("localhost", 0, 0.25f, 1, 0, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InspectorConfig("localhost", 7331, 0f, 1, 0, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InspectorConfig("localhost", 7331, 0.25f, 0, 0, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InspectorConfig("localhost", 7331, 0.25f, 1, -1, true)
        );
    }
}
