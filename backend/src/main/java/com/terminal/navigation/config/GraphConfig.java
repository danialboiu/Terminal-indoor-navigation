package com.terminal.navigation.config;

import com.terminal.navigation.routing.DijkstraRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfig {

    // Provides the shortest-path router used by the API when calculating routes.
    @Bean
    public DijkstraRouter router() {
        return new DijkstraRouter();
    }
}
