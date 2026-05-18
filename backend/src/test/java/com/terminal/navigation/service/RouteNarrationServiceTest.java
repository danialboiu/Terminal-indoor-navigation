package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.dto.NarrationInputStep;
import com.terminal.navigation.dto.NarrationResult;
import com.terminal.navigation.dto.PromptPayload;
import com.terminal.navigation.routing.PassengerProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteNarrationServiceTest {

    @Test
    @DisplayName("uses mock response when OPENAI_USE_MOCK is enabled")
    void usesMockResponse() {
        RouteNarrationService service = new RouteNarrationService(
                new ObjectMapper(),
                "",
                "gpt-4.1-mini",
                true,
                """
                        {
                          "summary": "Simple route summary.",
                          "instructions": [
                            { "fromStepIndex": 0, "toStepIndex": 0, "text": "Proceed to Security checkpoint.", "warnings": ["Prepare your boarding pass before the security gates."] },
                            { "fromStepIndex": 1, "toStepIndex": 1, "text": "You have arrived at Gates 30–31.", "warnings": [] }
                          ]
                        }
                        """,
                "",
                ""
        );

        NarrationResult result = service.narrate(
                sampleSteps(),
                PassengerProfile.ELDERLY,
                "Entrance A",
                "Gates 30–31"
        );

        assertEquals("mock", result.source());
        assertEquals("Simple route summary.", result.summary());
        assertEquals(2, result.instructions().size());
        assertEquals("Proceed to Security checkpoint.", result.instructions().get(0).text());
        assertEquals(List.of("Prepare your boarding pass before the security gates."), result.instructions().get(0).warnings());
    }

    @Test
    @DisplayName("throws when mock response is invalid")
    void throwsWhenMockInvalid() {
        RouteNarrationService service = new RouteNarrationService(
                new ObjectMapper(),
                "",
                "gpt-4.1-mini",
                true,
                "not-json",
                "",
                ""
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.narrate(
                        sampleSteps(),
                        PassengerProfile.ELDERLY,
                        "Entrance A",
                        "Gates 30–31"
                )
        );
        assertTrue(error.getMessage().contains("Unable to parse mock route instruction response"));
    }

    @Test
    @DisplayName("builds a prompt payload with context for AI")
    void buildsPromptPayload() throws Exception {
        RouteNarrationService service = new RouteNarrationService(
                new ObjectMapper(),
                "",
                "gpt-4.1-mini",
                false,
                "",
                "",
                ""
        );

        PromptPayload payload = service.buildPromptPayload(
                sampleSteps(),
                PassengerProfile.ELDERLY,
                "Entrance A",
                "Gates 30–31"
        );

        assertEquals("gpt-4.1-mini", payload.model());
        assertTrue(payload.systemPrompt().contains("GROUPING RULES"));
        assertTrue(payload.systemPrompt().contains("raw map labels"));
        assertTrue(payload.userPrompt().contains("Route from: Entrance A"));
        assertTrue(!payload.userPrompt().contains("Total cost"));
        assertTrue(payload.userPrompt().contains("InputSteps"));
        assertTrue(!payload.userPrompt().contains("fromRawLabel"));
    }

    private List<NarrationInputStep> sampleSteps() {
        return List.of(
                new NarrationInputStep(
                        "F0_ENT_A",
                        "F0_SECURITY_A",
                        "Entrance A",
                        "Security A",
                        "CORRIDOR",
                        0,
                        0,
                        "",
                        "SECURITY",
                        "Security checkpoint is after Entrance A."
                ),
                new NarrationInputStep(
                        "F0_SECURITY_A",
                        "B1_GATES_30_31",
                        "Security A",
                        "Gates 30–31",
                        "ESCALATOR",
                        0,
                        -1,
                        "SECURITY",
                        "",
                        ""
                )
        );
    }
}
