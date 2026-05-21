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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteNarrationServiceTest {

    @Test
    @DisplayName("builds a prompt payload with context for AI")
    void buildsPromptPayload() throws Exception {
        RouteNarrationService service = new RouteNarrationService(
                new ObjectMapper(),
                "",
                "gpt-4.1-mini"
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
                        "SECURITY"
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
                        ""
                )
        );
    }
}
