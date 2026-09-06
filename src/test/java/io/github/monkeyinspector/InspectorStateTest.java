package io.github.monkeyinspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InspectorStateTest {

    @Test
    void formatsIpv4InspectorUrl() {
        InspectorState state =
                new InspectorState(
                        InspectorConfig.defaults()
                                .withPort(9000)
                );

        assertEquals(
                "http://127.0.0.1:9000/",
                state.getInspectorUrl()
        );
    }

    @Test
    void formatsIpv6InspectorUrl() {
        InspectorState state =
                new InspectorState(
                        new InspectorConfig(
                                "::1",
                                7331,
                                0.25f,
                                1,
                                0,
                                false
                        )
                );

        assertEquals(
                "http://[::1]:7331/",
                state.getInspectorUrl()
        );
    }

    @Test
    void rejectsNullConfiguration() {
        assertThrows(
                NullPointerException.class,
                () -> new InspectorState(null)
        );
    }
}
