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

    @Bean
    public Graph graph() throws Exception {
        TerminalMapLoader loader = new TerminalMapLoader();
        TerminalMap map = loader.load("terminal-map.json");
        return new GraphBuilder().build(map);
    }

    @Bean
    public DijkstraRouter router() {
        return new DijkstraRouter();
    }
}
