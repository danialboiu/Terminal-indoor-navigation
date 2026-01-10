package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.TerminalMap;

import java.io.InputStream;

/**
 * Loads the terminal configuration from a JSON resource packaged with the application.
 */
public final class TerminalMapLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Loads the terminal layout from the application classpath.
     *
     * @param resourceName name of the JSON resource (e.g., "terminal-map.json")
     * @return terminal configuration model
     * @throws Exception if the resource is missing or cannot be deserialized
     */
    public TerminalMap load(String resourceName) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + resourceName);
            }
            return mapper.readValue(in, TerminalMap.class);
        }
    }
}
