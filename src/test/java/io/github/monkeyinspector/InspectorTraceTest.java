package io.github.monkeyinspector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectorTraceTest {

    @AfterEach
    void clearTrace() {
        InspectorTrace.clear();
    }

    @Test
    void recordsNestedSpansAndTheirEdge() {
        InspectorTrace.runSpan(
                "parent",
                () -> InspectorTrace.runSpan(
                        "child",
                        () -> {}
                )
        );

        JsonWriter writer =
                new JsonWriter();

        InspectorTrace.writeJson(writer);

        String json = writer.toString();

        assertTrue(json.contains("\"name\":\"parent\""));
        assertTrue(json.contains("\"name\":\"child\""));
        assertTrue(json.contains("\"from\":\"parent\""));
        assertTrue(json.contains("\"to\":\"child\""));
    }

    @Test
    void callSpanReturnsTheSuppliedValue() {
        assertEquals(
                42,
                InspectorTrace.callSpan("answer", () -> 42)
        );
    }

    @Test
    void runSpanRecordsUncheckedFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> InspectorTrace.runSpan(
                        "failure",
                        () -> {
                            throw new IllegalStateException("boom");
                        }
                )
        );

        JsonWriter writer =
                new JsonWriter();

        InspectorTrace.writeJson(writer);

        assertTrue(writer.toString().contains("\"failures\":1"));
    }
}
