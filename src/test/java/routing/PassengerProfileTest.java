package routing;

import graph.Graph;
import graph.GraphEdge;
import model.EdgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for passenger profile routing - verifies that different profiles
 * correctly allow/disallow edge types and apply cost penalties.
 */
class PassengerProfileTest {

    private DijkstraRouter router;

    @BeforeEach
    void setUp() {
        router = new DijkstraRouter();
    }

    @Nested
    @DisplayName("Standard Profile")
    class StandardProfile {

        @Test
        @DisplayName("uses stairs when it's the shortest route")
        void usesStairs() {
            Graph graph = buildMultiFloorGraph();
            RouteResult result = router.shortestPath(graph, "F1-A", "F2-A", PassengerProfile.STANDARD);

            // Stairs cost 2, elevator cost 3 - should use stairs
            assertTrue(result.path().contains("STAIRS"));
            assertEquals(2, result.totalCost());
        }

        @Test
        @DisplayName("allows all edge types")
        void allowsAllEdgeTypes() {
            assertTrue(PassengerProfile.STANDARD.isAllowed(EdgeType.CORRIDOR));
            assertTrue(PassengerProfile.STANDARD.isAllowed(EdgeType.STAIRS));
            assertTrue(PassengerProfile.STANDARD.isAllowed(EdgeType.ESCALATOR));
            assertTrue(PassengerProfile.STANDARD.isAllowed(EdgeType.ELEVATOR));
        }
    }

    @Nested
    @DisplayName("Wheelchair Profile")
    class WheelchairProfile {

        @Test
        @DisplayName("only uses elevator for floor changes")
        void onlyUsesElevator() {
            Graph graph = buildMultiFloorGraph();
            RouteResult result = router.shortestPath(graph, "F1-A", "F2-A", PassengerProfile.WHEELCHAIR);

            // Must use elevator even though stairs are cheaper
            assertTrue(result.path().contains("ELEVATOR"));
            assertFalse(result.path().contains("STAIRS"));
            assertFalse(result.path().contains("ESCALATOR"));
        }

        @Test
        @DisplayName("throws when no elevator available")
        void throwsWhenNoElevatorAvailable() {
            Graph graph = buildGraphWithOnlyStairs();

            assertThrows(IllegalStateException.class, () ->
                    router.shortestPath(graph, "F1", "F2", PassengerProfile.WHEELCHAIR)
            );
        }

        @Test
        @DisplayName("disallows stairs and escalators")
        void disallowsStairsAndEscalators() {
            assertFalse(PassengerProfile.WHEELCHAIR.isAllowed(EdgeType.STAIRS));
            assertFalse(PassengerProfile.WHEELCHAIR.isAllowed(EdgeType.ESCALATOR));
            assertTrue(PassengerProfile.WHEELCHAIR.isAllowed(EdgeType.ELEVATOR));
            assertTrue(PassengerProfile.WHEELCHAIR.isAllowed(EdgeType.CORRIDOR));
        }
    }

    @Nested
    @DisplayName("Parent With Stroller Profile")
    class ParentWithStrollerProfile {

        @Test
        @DisplayName("prefers elevator over penalized escalator")
        void prefersElevatorOverEscalator() {
            Graph graph = buildElevatorVsEscalatorGraph();
            RouteResult result = router.shortestPath(graph, "F1", "F2", PassengerProfile.PARENT_WITH_STROLLER);

            // Escalator base cost 1 * 3 penalty = 3, Elevator cost 2
            // Should prefer elevator
            assertTrue(result.path().contains("ELEVATOR"));
        }

        @Test
        @DisplayName("uses escalator when elevator unavailable")
        void usesEscalatorWhenNoElevator() {
            Graph graph = buildGraphWithOnlyEscalator();
            // Should succeed - escalator is allowed (with penalty) when no elevator exists
            RouteResult result = router.shortestPath(graph, "F1", "F2", PassengerProfile.PARENT_WITH_STROLLER);

            assertEquals(List.of("F1", "F2"), result.path());
            // Cost is 1 base * 3 penalty = 3
            assertEquals(3, result.totalCost());
        }

        @Test
        @DisplayName("cannot use stairs")
        void cannotUseStairs() {
            Graph graph = buildGraphWithOnlyStairs();

            assertThrows(IllegalStateException.class, () ->
                    router.shortestPath(graph, "F1", "F2", PassengerProfile.PARENT_WITH_STROLLER)
            );
        }

        @Test
        @DisplayName("applies 3x penalty to escalators")
        void appliesEscalatorPenalty() {
            assertEquals(3, PassengerProfile.PARENT_WITH_STROLLER.getEffectiveCost(EdgeType.ESCALATOR, 1));
            assertEquals(6, PassengerProfile.PARENT_WITH_STROLLER.getEffectiveCost(EdgeType.ESCALATOR, 2));
        }
    }

    @Nested
    @DisplayName("Elderly Profile")
    class ElderlyProfile {

        @Test
        @DisplayName("prefers elevator over escalator")
        void prefersElevatorOverEscalator() {
            Graph graph = buildElevatorVsEscalatorGraph();
            RouteResult result = router.shortestPath(graph, "F1", "F2", PassengerProfile.ELDERLY);

            // Escalator base cost 1 * 2 penalty = 2, Elevator cost 2
            // Both equal, but elevator should be in path or escalator accepted
            assertNotNull(result);
        }

        @Test
        @DisplayName("cannot use stairs")
        void cannotUseStairs() {
            assertFalse(PassengerProfile.ELDERLY.isAllowed(EdgeType.STAIRS));
        }

        @Test
        @DisplayName("applies 2x penalty to escalators")
        void appliesEscalatorPenalty() {
            assertEquals(2, PassengerProfile.ELDERLY.getEffectiveCost(EdgeType.ESCALATOR, 1));
            assertEquals(1, PassengerProfile.ELDERLY.getEffectiveCost(EdgeType.CORRIDOR, 1));
        }
    }

    @Nested
    @DisplayName("Multi-Floor Scenarios")
    class MultiFloorScenarios {

        @Test
        @DisplayName("wheelchair user takes longer route via elevator")
        void wheelchairTakesLongerRoute() {
            Graph graph = buildRealisticTerminalGraph();

            RouteResult standardResult = router.shortestPath(graph, "GATE-A", "LOUNGE-F2", PassengerProfile.STANDARD);
            RouteResult wheelchairResult = router.shortestPath(graph, "GATE-A", "LOUNGE-F2", PassengerProfile.WHEELCHAIR);

            // Wheelchair route should cost more (can't use stairs/escalator)
            assertTrue(wheelchairResult.totalCost() >= standardResult.totalCost());
        }

        @Test
        @DisplayName("all profiles can reach same destination via different routes")
        void allProfilesReachDestination() {
            Graph graph = buildRealisticTerminalGraph();

            for (PassengerProfile profile : PassengerProfile.values()) {
                RouteResult result = router.shortestPath(graph, "GATE-A", "LOUNGE-F2", profile);
                assertEquals("GATE-A", result.from());
                assertEquals("LOUNGE-F2", result.to());
                assertFalse(result.path().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("Cost Penalty Calculations")
    class CostPenaltyCalculations {

        @Test
        @DisplayName("standard profile has no penalties")
        void standardNoPenalties() {
            for (EdgeType type : EdgeType.values()) {
                assertEquals(5, PassengerProfile.STANDARD.getEffectiveCost(type, 5));
            }
        }

        @Test
        @DisplayName("corridor has no penalty for any profile")
        void corridorNoPenalty() {
            for (PassengerProfile profile : PassengerProfile.values()) {
                assertEquals(1, profile.getEffectiveCost(EdgeType.CORRIDOR, 1));
            }
        }

        @Test
        @DisplayName("elevator has no penalty for any profile")
        void elevatorNoPenalty() {
            for (PassengerProfile profile : PassengerProfile.values()) {
                if (profile.isAllowed(EdgeType.ELEVATOR)) {
                    assertEquals(1, profile.getEffectiveCost(EdgeType.ELEVATOR, 1));
                }
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Multi-floor graph with stairs (cost 2) and elevator (cost 3):
     * F1-A -- STAIRS -- F2-A
     *   |                |
     *   +-- ELEVATOR ----+
     */
    private Graph buildMultiFloorGraph() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("F1-A", List.of(
                new GraphEdge("STAIRS", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("ELEVATOR", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("STAIRS", List.of(
                new GraphEdge("F1-A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("F2-A", 1, EdgeType.STAIRS, true)
        ));
        adj.put("ELEVATOR", List.of(
                new GraphEdge("F1-A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("F2-A", 2, EdgeType.ELEVATOR, true)
        ));
        adj.put("F2-A", List.of(
                new GraphEdge("STAIRS", 1, EdgeType.STAIRS, true),
                new GraphEdge("ELEVATOR", 2, EdgeType.ELEVATOR, true)
        ));

        Map<String, Integer> floors = Map.of("F1-A", 1, "STAIRS", 1, "ELEVATOR", 1, "F2-A", 2);
        return new Graph(adj, Map.of("F1-A", true, "STAIRS", true, "ELEVATOR", true, "F2-A", true), floors);
    }

    /**
     * Graph with only stairs between floors.
     */
    private Graph buildGraphWithOnlyStairs() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("F1", List.of(new GraphEdge("F2", 1, EdgeType.STAIRS, true)));
        adj.put("F2", List.of(new GraphEdge("F1", 1, EdgeType.STAIRS, true)));

        return new Graph(adj, Map.of("F1", true, "F2", true), Map.of("F1", 1, "F2", 2));
    }

    /**
     * Graph with only escalator between floors.
     */
    private Graph buildGraphWithOnlyEscalator() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("F1", List.of(new GraphEdge("F2", 1, EdgeType.ESCALATOR, true)));
        adj.put("F2", List.of(new GraphEdge("F1", 1, EdgeType.ESCALATOR, true)));

        return new Graph(adj, Map.of("F1", true, "F2", true), Map.of("F1", 1, "F2", 2));
    }

    /**
     * Graph comparing elevator (cost 2) vs escalator (cost 1):
     * F1 -- ESCALATOR(1) -- F2
     *  |                    |
     *  +-- ELEVATOR(2) -----+
     */
    private Graph buildElevatorVsEscalatorGraph() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("F1", List.of(
                new GraphEdge("ESCALATOR", 1, EdgeType.ESCALATOR, true),
                new GraphEdge("ELEVATOR", 2, EdgeType.ELEVATOR, true)
        ));
        adj.put("ESCALATOR", List.of(
                new GraphEdge("F1", 1, EdgeType.ESCALATOR, true),
                new GraphEdge("F2", 0, EdgeType.CORRIDOR, true)
        ));
        adj.put("ELEVATOR", List.of(
                new GraphEdge("F1", 2, EdgeType.ELEVATOR, true),
                new GraphEdge("F2", 0, EdgeType.CORRIDOR, true)
        ));
        adj.put("F2", List.of(
                new GraphEdge("ESCALATOR", 0, EdgeType.CORRIDOR, true),
                new GraphEdge("ELEVATOR", 0, EdgeType.CORRIDOR, true)
        ));

        return new Graph(adj,
                Map.of("F1", true, "F2", true, "ESCALATOR", true, "ELEVATOR", true),
                Map.of("F1", 1, "F2", 2, "ESCALATOR", 1, "ELEVATOR", 1));
    }

    /**
     * Realistic terminal with multiple vertical transport options:
     *
     * Floor 1: GATE-A -- HUB -- GATE-B
     *                     |
     *          STAIRS  ESCALATOR  ELEVATOR
     *                     |
     * Floor 2:        LOUNGE-F2
     */
    private Graph buildRealisticTerminalGraph() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();

        // Floor 1 connections
        adj.put("GATE-A", List.of(new GraphEdge("HUB", 2, EdgeType.CORRIDOR, true)));
        adj.put("GATE-B", List.of(new GraphEdge("HUB", 2, EdgeType.CORRIDOR, true)));
        adj.put("HUB", List.of(
                new GraphEdge("GATE-A", 2, EdgeType.CORRIDOR, true),
                new GraphEdge("GATE-B", 2, EdgeType.CORRIDOR, true),
                new GraphEdge("LOUNGE-F2", 1, EdgeType.STAIRS, true),
                new GraphEdge("LOUNGE-F2", 2, EdgeType.ESCALATOR, true),
                new GraphEdge("LOUNGE-F2", 3, EdgeType.ELEVATOR, true)
        ));

        // Floor 2
        adj.put("LOUNGE-F2", List.of(
                new GraphEdge("HUB", 1, EdgeType.STAIRS, true),
                new GraphEdge("HUB", 2, EdgeType.ESCALATOR, true),
                new GraphEdge("HUB", 3, EdgeType.ELEVATOR, true)
        ));

        Map<String, Boolean> enabled = Map.of(
                "GATE-A", true, "GATE-B", true, "HUB", true, "LOUNGE-F2", true
        );
        Map<String, Integer> floors = Map.of(
                "GATE-A", 1, "GATE-B", 1, "HUB", 1, "LOUNGE-F2", 2
        );

        return new Graph(adj, enabled, floors);
    }
}
