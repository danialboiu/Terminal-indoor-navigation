package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphBuilder;
import com.terminal.navigation.model.Node;
import com.terminal.navigation.model.TerminalMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class TerminalMapProvider {
    private static final String CLASSPATH_MAP_RESOURCE = "terminal-map.json";

    private final ObjectMapper objectMapper;
    private final GraphBuilder graphBuilder = new GraphBuilder();
    private final Path mapFile;

    private TerminalMapSnapshot currentSnapshot;
    private long lastModifiedMillis = -1;

    public TerminalMapProvider(
            ObjectMapper objectMapper,
            @Value("${terminal.map.file:src/main/resources/terminal-map.json}") String mapFile
    ) {
        this.objectMapper = objectMapper;
        this.mapFile = Path.of(mapFile == null || mapFile.isBlank()
                ? "src/main/resources/terminal-map.json"
                : mapFile.trim());
    }

    public synchronized TerminalMapSnapshot current() {
        reloadIfChanged();
        return currentSnapshot;
    }

    private void reloadIfChanged() {
        try {
            long modifiedMillis = currentModifiedMillis();
            if (currentSnapshot != null && modifiedMillis == lastModifiedMillis) {
                return;
            }

            TerminalMap terminalMap = loadTerminalMap();
            Graph graph = graphBuilder.build(terminalMap);
            currentSnapshot = new TerminalMapSnapshot(
                    terminalMap,
                    graph,
                    buildNodeLookup(terminalMap)
            );
            lastModifiedMillis = modifiedMillis;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load terminal map from " + mapFile + ".", e);
        }
    }

    private long currentModifiedMillis() throws Exception {
        if (Files.exists(mapFile)) {
            return Files.getLastModifiedTime(mapFile).toMillis();
        }
        return 0;
    }

    private TerminalMap loadTerminalMap() throws Exception {
        if (Files.exists(mapFile)) {
            return objectMapper.readValue(mapFile.toFile(), TerminalMap.class);
        }

        try (InputStream inputStream = TerminalMapProvider.class
                .getClassLoader()
                .getResourceAsStream(CLASSPATH_MAP_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing classpath resource: " + CLASSPATH_MAP_RESOURCE);
            }
            return objectMapper.readValue(inputStream, TerminalMap.class);
        }
    }

    private static Map<String, Node> buildNodeLookup(TerminalMap terminalMap) {
        Map<String, Node> lookup = new HashMap<>();
        if (terminalMap.nodes != null) {
            for (Node node : terminalMap.nodes) {
                if (node != null && node.id != null && !node.id.isBlank()) {
                    lookup.put(node.id, node);
                }
            }
        }
        return Map.copyOf(lookup);
    }

    public record TerminalMapSnapshot(
            TerminalMap terminalMap,
            Graph graph,
            Map<String, Node> nodeById
    ) {}
}
