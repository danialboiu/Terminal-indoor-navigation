package routing;

import graph.Graph;
import graph.GraphEdge;
import model.EdgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DijkstraRouter covering basic routing, disabled infrastructure,
 * and edge cases.
 */
class DijkstraRouterTest {

    private DijkstraRouter router;

    @BeforeEach
    void setUp() {
        router = new DijkstraRouter();
    }

    @Nested
    @DisplayName("Basic Routing")
    class BasicRouting {

        @Test
        @DisplayName("finds direct path between adjacent nodes")
        void findsDirectPath() {
            Graph graph = buildSimpleGraph();
            RouteResult result = router.shortestPath(graph, "A", "B");

            assertEquals("A", result.from());
            assertEquals("B", result.to());
            assertEquals(List.of("A", "B"), result.path());
            assertEquals(1, result.totalCost());
        }

        @Test
        @DisplayName("finds shortest path through multiple nodes")
        void findsShortestPathThroughMultipleNodes() {
            Graph graph = buildSimpleGraph();
            RouteResult result = router.shortestPath(graph, "A", "C");

            assertEquals(List.of("A", "B", "C"), result.path());
            assertEquals(2, result.totalCost());
        }

        @Test
        @DisplayName("returns same node when start equals destination")
        void sameStartAndDestination() {
            Graph graph = buildSimpleGraph();
            RouteResult result = router.shortestPath(graph, "A", "A");

            assertEquals(List.of("A"), result.path());
            assertEquals(0, result.totalCost());
        }

        @Test
        @DisplayName("chooses lower cost path when multiple routes exist")
        void choosesLowerCostPath() {
            // A --1-- B --1-- D
            // |               |
            // +------10-------+
            Graph graph = buildGraphWithAlternativePaths();
            RouteResult result = router.shortestPath(graph, "A", "D");

            // Should take A -> B -> D (cost 2) instead of A -> D (cost 10)
            assertEquals(List.of("A", "B", "D"), result.path());
            assertEquals(2, result.totalCost());
        }
    }

    @Nested
    @DisplayName("Disabled Infrastructure")
    class DisabledInfrastructure {

        @Test
        @DisplayName("avoids disabled edges")
        void avoidsDisabledEdges() {
            Graph graph = buildGraphWithDisabledEdge();
            RouteResult result = router.shortestPath(graph, "A", "C");

            // Direct A->C is disabled, must go A->B->C
            assertEquals(List.of("A", "B", "C"), result.path());
            assertEquals(2, result.totalCost());
        }

        @Test
        @DisplayName("throws exception when only route uses disabled edge")
        void throwsWhenOnlyRouteDisabled() {
            Graph graph = buildGraphWithOnlyDisabledRoute();

            assertThrows(IllegalStateException.class, () ->
                    router.shortestPath(graph, "A", "B")
            );
        }

        @Test
        @DisplayName("avoids disabled nodes")
        void avoidsDisabledNodes() {
            Graph graph = buildGraphWithDisabledNode();
            RouteResult result = router.shortestPath(graph, "A", "D");

            // Node B is disabled, must go A->C->D
            assertEquals(List.of("A", "C", "D"), result.path());
        }

        @Test
        @DisplayName("throws when start node is disabled")
        void throwsWhenStartNodeDisabled() {
            Graph graph = buildGraphWithDisabledNode();

            assertThrows(IllegalArgumentException.class, () ->
                    router.shortestPath(graph, "B", "D")
            );
        }

        @Test
        @DisplayName("throws when destination node is disabled")
        void throwsWhenDestinationNodeDisabled() {
            Graph graph = buildGraphWithDisabledNode();

            assertThrows(IllegalArgumentException.class, () ->
                    router.shortestPath(graph, "A", "B")
            );
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws for unknown start node")
        void throwsForUnknownStartNode() {
            Graph graph = buildSimpleGraph();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    router.shortestPath(graph, "UNKNOWN", "A")
            );
            assertTrue(ex.getMessage().contains("Unknown start node"));
        }

        @Test
        @DisplayName("throws for unknown destination node")
        void throwsForUnknownDestinationNode() {
            Graph graph = buildSimpleGraph();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    router.shortestPath(graph, "A", "UNKNOWN")
            );
            assertTrue(ex.getMessage().contains("Unknown destination node"));
        }

        @Test
        @DisplayName("throws when no route exists")
        void throwsWhenNoRouteExists() {
            Graph graph = buildDisconnectedGraph();

            assertThrows(IllegalStateException.class, () ->
                    router.shortestPath(graph, "A", "Z")
            );
        }

        @Test
        @DisplayName("throws for null graph")
        void throwsForNullGraph() {
            assertThrows(NullPointerException.class, () ->
                    router.shortestPath(null, "A", "B")
            );
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Simple linear graph: A --1-- B --1-- C
     */
    private Graph buildSimpleGraph() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(new GraphEdge("B", 1, EdgeType.CORRIDOR, true)));
        adj.put("B", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("C", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("C", List.of(new GraphEdge("B", 1, EdgeType.CORRIDOR, true)));

        return new Graph(adj, Map.of("A", true, "B", true, "C", true), emptyMap());
    }

    /**
     * Graph with alternative paths:
     * A --1-- B --1-- D
     * |               |
     * +------10-------+
     */
    private Graph buildGraphWithAlternativePaths() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("D", 10, EdgeType.CORRIDOR, true)
        ));
        adj.put("B", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("D", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("D", List.of(
                new GraphEdge("A", 10, EdgeType.CORRIDOR, true),
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true)
        ));

        return new Graph(adj, Map.of("A", true, "B", true, "D", true), emptyMap());
    }

    /**
     * Graph with disabled edge:
     * A --X-- C (disabled)
     * |       |
     * B ------+
     */
    private Graph buildGraphWithDisabledEdge() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("C", 1, EdgeType.CORRIDOR, false)  // disabled
        ));
        adj.put("B", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("C", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("C", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, false),  // disabled
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true)
        ));

        return new Graph(adj, Map.of("A", true, "B", true, "C", true), emptyMap());
    }

    /**
     * Graph where only route is disabled: A --X-- B
     */
    private Graph buildGraphWithOnlyDisabledRoute() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(new GraphEdge("B", 1, EdgeType.CORRIDOR, false)));
        adj.put("B", List.of(new GraphEdge("A", 1, EdgeType.CORRIDOR, false)));

        return new Graph(adj, Map.of("A", true, "B", true), emptyMap());
    }

    /**
     * Graph with disabled node B:
     * A -- B(disabled) -- D
     * |                   |
     * C ------------------+
     */
    private Graph buildGraphWithDisabledNode() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("C", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("B", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("D", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("C", List.of(
                new GraphEdge("A", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("D", 1, EdgeType.CORRIDOR, true)
        ));
        adj.put("D", List.of(
                new GraphEdge("B", 1, EdgeType.CORRIDOR, true),
                new GraphEdge("C", 1, EdgeType.CORRIDOR, true)
        ));

        return new Graph(adj, Map.of("A", true, "B", false, "C", true, "D", true), emptyMap());
    }

    /**
     * Disconnected graph: A -- B    Z (isolated)
     */
    private Graph buildDisconnectedGraph() {
        Map<String, List<GraphEdge>> adj = new HashMap<>();
        adj.put("A", List.of(new GraphEdge("B", 1, EdgeType.CORRIDOR, true)));
        adj.put("B", List.of(new GraphEdge("A", 1, EdgeType.CORRIDOR, true)));
        adj.put("Z", List.of());

        return new Graph(adj, Map.of("A", true, "B", true, "Z", true), emptyMap());
    }
}
