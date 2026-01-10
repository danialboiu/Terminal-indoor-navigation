package graph;

import model.EdgeDef;
import model.EdgeType;
import model.Node;
import model.TerminalMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GraphBuilder covering validation and graph construction.
 */
class GraphBuilderTest {

    private GraphBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new GraphBuilder();
    }

    @Nested
    @DisplayName("Successful Graph Building")
    class SuccessfulBuilding {

        @Test
        @DisplayName("builds graph from valid terminal map")
        void buildsGraphFromValidMap() {
            TerminalMap map = createValidTerminalMap();
            Graph graph = builder.build(map);

            assertNotNull(graph);
            assertTrue(graph.hasNode("A"));
            assertTrue(graph.hasNode("B"));
        }

        @Test
        @DisplayName("creates bidirectional edges correctly")
        void createsBidirectionalEdges() {
            TerminalMap map = createValidTerminalMap();
            Graph graph = builder.build(map);

            // Edge A->B should exist
            List<GraphEdge> edgesFromA = graph.edgesFrom("A");
            assertTrue(edgesFromA.stream().anyMatch(e -> e.to().equals("B")));

            // Reverse edge B->A should also exist (bidirectional)
            List<GraphEdge> edgesFromB = graph.edgesFrom("B");
            assertTrue(edgesFromB.stream().anyMatch(e -> e.to().equals("A")));
        }

        @Test
        @DisplayName("defaults enabled to true when not specified")
        void defaultsEnabledToTrue() {
            TerminalMap map = createValidTerminalMap();
            Graph graph = builder.build(map);

            assertTrue(graph.isNodeEnabled("A"));
            assertTrue(graph.isNodeEnabled("B"));

            // Check edge enabled
            List<GraphEdge> edges = graph.edgesFrom("A");
            assertTrue(edges.stream().allMatch(GraphEdge::enabled));
        }

        @Test
        @DisplayName("respects enabled=false on edges")
        void respectsDisabledEdges() {
            TerminalMap map = createMapWithDisabledEdge();
            Graph graph = builder.build(map);

            List<GraphEdge> edges = graph.edgesFrom("A");
            GraphEdge disabledEdge = edges.stream()
                    .filter(e -> e.to().equals("B"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(disabledEdge.enabled());
        }

        @Test
        @DisplayName("respects enabled=false on nodes")
        void respectsDisabledNodes() {
            TerminalMap map = createMapWithDisabledNode();
            Graph graph = builder.build(map);

            assertTrue(graph.isNodeEnabled("A"));
            assertFalse(graph.isNodeEnabled("B"));
        }

        @Test
        @DisplayName("defaults floor to 1 when not specified")
        void defaultsFloorToOne() {
            TerminalMap map = createValidTerminalMap();
            Graph graph = builder.build(map);

            assertEquals(1, graph.getNodeFloor("A"));
            assertEquals(1, graph.getNodeFloor("B"));
        }

        @Test
        @DisplayName("preserves floor values from nodes")
        void preservesFloorValues() {
            TerminalMap map = createMultiFloorMap();
            Graph graph = builder.build(map);

            assertEquals(1, graph.getNodeFloor("F1"));
            assertEquals(2, graph.getNodeFloor("F2"));
        }

        @Test
        @DisplayName("preserves edge types")
        void preservesEdgeTypes() {
            TerminalMap map = createMapWithDifferentEdgeTypes();
            Graph graph = builder.build(map);

            List<GraphEdge> edges = graph.edgesFrom("A");

            assertTrue(edges.stream().anyMatch(e -> e.type() == EdgeType.CORRIDOR));
            assertTrue(edges.stream().anyMatch(e -> e.type() == EdgeType.ELEVATOR));
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("throws for null terminal map")
        void throwsForNullMap() {
            assertThrows(NullPointerException.class, () -> builder.build(null));
        }

        @Test
        @DisplayName("throws for empty nodes list")
        void throwsForEmptyNodes() {
            TerminalMap map = new TerminalMap();
            map.nodes = new ArrayList<>();
            map.edges = List.of(createEdge("A", "B"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("No nodes defined"));
        }

        @Test
        @DisplayName("throws for empty edges list")
        void throwsForEmptyEdges() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"));
            map.edges = new ArrayList<>();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("No edges defined"));
        }

        @Test
        @DisplayName("throws for duplicate node IDs")
        void throwsForDuplicateNodeIds() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"), createNode("A"));  // duplicate
            map.edges = List.of(createEdge("A", "A"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("Duplicate node id"));
        }

        @Test
        @DisplayName("throws for edge referencing unknown source node")
        void throwsForUnknownSourceNode() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"));
            map.edges = List.of(createEdge("UNKNOWN", "A"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("unknown source node"));
        }

        @Test
        @DisplayName("throws for edge referencing unknown destination node")
        void throwsForUnknownDestinationNode() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"));
            map.edges = List.of(createEdge("A", "UNKNOWN"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("unknown destination node"));
        }

        @Test
        @DisplayName("throws for edge with zero cost")
        void throwsForZeroCost() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"), createNode("B"));
            EdgeDef edge = createEdge("A", "B");
            edge.cost = 0;
            map.edges = List.of(edge);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("Invalid edge cost"));
        }

        @Test
        @DisplayName("throws for edge with negative cost")
        void throwsForNegativeCost() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"), createNode("B"));
            EdgeDef edge = createEdge("A", "B");
            edge.cost = -1;
            map.edges = List.of(edge);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("Invalid edge cost"));
        }

        @Test
        @DisplayName("throws for edge with missing type")
        void throwsForMissingEdgeType() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("A"), createNode("B"));
            EdgeDef edge = createEdge("A", "B");
            edge.type = null;
            map.edges = List.of(edge);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("Missing edge type"));
        }

        @Test
        @DisplayName("throws for node with blank ID")
        void throwsForBlankNodeId() {
            TerminalMap map = new TerminalMap();
            map.nodes = List.of(createNode("  "));  // blank
            map.edges = List.of(createEdge("A", "B"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.build(map));
            assertTrue(ex.getMessage().contains("missing or empty id"));
        }
    }

    // ==================== Helper Methods ====================

    private Node createNode(String id) {
        Node node = new Node();
        node.id = id;
        node.label = "Node " + id;
        node.description = "Description for " + id;
        return node;
    }

    private Node createNode(String id, int floor) {
        Node node = createNode(id);
        node.floor = floor;
        return node;
    }

    private Node createNode(String id, boolean enabled) {
        Node node = createNode(id);
        node.enabled = enabled;
        return node;
    }

    private EdgeDef createEdge(String from, String to) {
        EdgeDef edge = new EdgeDef();
        edge.from = from;
        edge.to = to;
        edge.cost = 1;
        edge.bidirectional = true;
        edge.type = EdgeType.CORRIDOR;
        return edge;
    }

    private EdgeDef createEdge(String from, String to, EdgeType type) {
        EdgeDef edge = createEdge(from, to);
        edge.type = type;
        return edge;
    }

    private TerminalMap createValidTerminalMap() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(createNode("A"), createNode("B"));
        map.edges = List.of(createEdge("A", "B"));
        return map;
    }

    private TerminalMap createMapWithDisabledEdge() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(createNode("A"), createNode("B"));
        EdgeDef edge = createEdge("A", "B");
        edge.enabled = false;
        map.edges = List.of(edge);
        return map;
    }

    private TerminalMap createMapWithDisabledNode() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(createNode("A"), createNode("B", false));
        map.edges = List.of(createEdge("A", "B"));
        return map;
    }

    private TerminalMap createMultiFloorMap() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(createNode("F1", 1), createNode("F2", 2));
        map.edges = List.of(createEdge("F1", "F2", EdgeType.ELEVATOR));
        return map;
    }

    private TerminalMap createMapWithDifferentEdgeTypes() {
        TerminalMap map = new TerminalMap();
        map.nodes = List.of(createNode("A"), createNode("B"), createNode("C"));
        map.edges = List.of(
                createEdge("A", "B", EdgeType.CORRIDOR),
                createEdge("A", "C", EdgeType.ELEVATOR)
        );
        return map;
    }
}
