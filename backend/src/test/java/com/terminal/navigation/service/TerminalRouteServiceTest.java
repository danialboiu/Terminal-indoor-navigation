package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.dto.NarrationInputStep;
import com.terminal.navigation.dto.NarrationInstruction;
import com.terminal.navigation.dto.NarrationResult;
import com.terminal.navigation.dto.RouteInstructionsResponse;
import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphBuilder;
import com.terminal.navigation.model.EdgeDef;
import com.terminal.navigation.model.EdgeType;
import com.terminal.navigation.model.Node;
import com.terminal.navigation.model.TerminalMap;
import com.terminal.navigation.routing.DijkstraRouter;
import com.terminal.navigation.routing.PassengerProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalRouteServiceTest {

    @Test
    @DisplayName("returns AI narration when generation succeeds")
    void returnsAiNarrationWhenGenerationSucceeds() {
        RouteInstructionsResponse response = serviceWith(
                simpleTerminalMap(),
                new StubNarrationService(new NarrationResult(
                        "AI summary.",
                        List.of(new NarrationInstruction(0, 1, "AI generated guidance.", List.of()))
                ))
        ).routeInstructions("A", "C", "PASSENGER");

        assertEquals("AI summary.", response.summary());
        assertEquals(1, response.instructions().size());
        assertEquals("AI generated guidance.", response.instructions().get(0).text());
    }

    @Test
    @DisplayName("falls back to route points when AI generation fails")
    void fallsBackToRoutePointsWhenAiGenerationFails() {
        RouteInstructionsResponse response = serviceWith(
                simpleTerminalMap(),
                new StubNarrationService(new IllegalStateException("AI unavailable"))
        ).routeInstructions("A", "C", "PASSENGER");

        assertTrue(response.summary().startsWith("AI instructions are unavailable."));
        assertEquals(1, response.instructions().size());
        assertEquals(
                "Follow these points: Entrance A -> Security Checkpoint -> Gate C.\nYou have arrived at Gate C.",
                response.instructions().get(0).text()
        );
        assertEquals(
                List.of("Prepare your boarding pass before the security gates."),
                response.instructions().get(0).warnings()
        );
    }

    @Test
    @DisplayName("fallback skips technical labels and preserves checkpoint warnings")
    void fallbackSkipsTechnicalLabelsAndPreservesWarnings() {
        RouteInstructionsResponse response = serviceWith(
                mapWithTechnicalNodesAndPassportControl(),
                new StubNarrationService(new IllegalStateException("Invalid AI response"))
        ).routeInstructions("A", "D", "PASSENGER");

        String fallbackText = response.instructions().get(0).text();
        assertTrue(fallbackText.contains("Entrance A -> Security Checkpoint -> Passport Control -> Gate D"));
        assertFalse(fallbackText.contains("Post-Security Area"));
        assertFalse(fallbackText.contains("International Corridor"));
        assertEquals(
                List.of(
                        "Prepare your boarding pass before the security gates.",
                        "Keep your passport and boarding pass ready for passport control."
                ),
                response.instructions().get(0).warnings()
        );
    }

    private TerminalRouteService serviceWith(TerminalMap terminalMap, RouteNarrationService narrationService) {
        Graph graph = new GraphBuilder().build(terminalMap);
        TerminalMapProvider.TerminalMapSnapshot snapshot = new TerminalMapProvider.TerminalMapSnapshot(
                terminalMap,
                graph,
                nodeLookup(terminalMap)
        );
        return new TerminalRouteService(
                new FixedTerminalMapProvider(snapshot),
                new DijkstraRouter(),
                narrationService
        );
    }

    private Map<String, Node> nodeLookup(TerminalMap terminalMap) {
        Map<String, Node> lookup = new HashMap<>();
        for (Node node : terminalMap.nodes) {
            lookup.put(node.id, node);
        }
        return Map.copyOf(lookup);
    }

    private TerminalMap simpleTerminalMap() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(
                node("A", "Entrance A", ""),
                node("B", "Security Checkpoint", "SECURITY"),
                node("C", "Gate C", "")
        );
        map.edges = List.of(
                edge("A", "B"),
                edge("B", "C")
        );
        return map;
    }

    private TerminalMap mapWithTechnicalNodesAndPassportControl() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(
                node("A", "Entrance A", ""),
                node("B", "Security Checkpoint", "SECURITY"),
                node("POST", "Post-Security Area", ""),
                node("P", "Passport Control", "PASSPORT_CONTROL"),
                node("H", "International Corridor", ""),
                node("D", "Gate D", "")
        );
        map.edges = List.of(
                edge("A", "B"),
                edge("B", "POST"),
                edge("POST", "P"),
                edge("P", "H"),
                edge("H", "D")
        );
        return map;
    }

    private Node node(String id, String label, String checkpointType) {
        Node node = new Node();
        node.id = id;
        node.label = label;
        node.enabled = true;
        node.floor = 0.0;
        node.category = "Test";
        node.checkpointType = checkpointType;
        return node;
    }

    private EdgeDef edge(String from, String to) {
        EdgeDef edge = new EdgeDef();
        edge.from = from;
        edge.to = to;
        edge.cost = 1.0;
        edge.bidirectional = true;
        edge.type = EdgeType.CORRIDOR;
        edge.enabled = true;
        return edge;
    }

    private static final class FixedTerminalMapProvider extends TerminalMapProvider {
        private final TerminalMapSnapshot snapshot;

        private FixedTerminalMapProvider(TerminalMapSnapshot snapshot) {
            super(new ObjectMapper(), "unused.json");
            this.snapshot = snapshot;
        }

        @Override
        public synchronized TerminalMapSnapshot current() {
            return snapshot;
        }
    }

    private static final class StubNarrationService extends RouteNarrationService {
        private final NarrationResult result;
        private final RuntimeException failure;

        private StubNarrationService(NarrationResult result) {
            super(new ObjectMapper(), "unused-key", "test-model");
            this.result = result;
            this.failure = null;
        }

        private StubNarrationService(RuntimeException failure) {
            super(new ObjectMapper(), "unused-key", "test-model");
            this.result = null;
            this.failure = failure;
        }

        @Override
        public NarrationResult narrate(
                List<NarrationInputStep> inputSteps,
                PassengerProfile profile,
                String fromLabel,
                String toLabel
        ) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
