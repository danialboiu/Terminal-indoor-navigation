package com.terminal.navigation.config;

import com.terminal.navigation.graph.Graph;
import com.terminal.navigation.graph.GraphBuilder;
import com.terminal.navigation.io.TerminalMapLoader;
import com.terminal.navigation.model.TerminalMap;
import com.terminal.navigation.routing.DijkstraRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    // Loads the terminal layout definition from terminal-map.json.
    @Bean
    public TerminalMap terminalMap() throws Exception {
        TerminalMapLoader loader = new TerminalMapLoader();
        return loader.load("terminal-map.json");
    }

    // Converts the loaded terminal layout into the in-memory graph used for routing.
    @Bean
    public Graph graph(TerminalMap terminalMap) {
        return new GraphBuilder().build(terminalMap);
    }

    // Provides the shortest-path router used by the API when calculating routes.
    @Bean
    public DijkstraRouter router() {
        return new DijkstraRouter();
    }
}
