package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.dto.AiMessageRequest;
import com.terminal.navigation.dto.AiMessageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class AiMessageService {
    private static final String CHAT_SYSTEM_PROMPT_RESOURCE = "ai/ai-message-system-prompt.txt";
    private static final String ROUTE_SYSTEM_PROMPT_RESOURCE = "ai/route-instructions-system-prompt.txt";
    private static final Path LOCAL_CHAT_SYSTEM_PROMPT = Path.of("src/main/resources", CHAT_SYSTEM_PROMPT_RESOURCE);
    private static final Path LOCAL_ROUTE_SYSTEM_PROMPT = Path.of("src/main/resources", ROUTE_SYSTEM_PROMPT_RESOURCE);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public AiMessageService(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${OPENAI_MODEL:gpt-4.1-mini}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-4.1-mini" : model.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public AiMessageResponse answer(AiMessageRequest request) {
        String cleanMessage = request == null || request.message() == null ? "" : request.message().trim();
        if (cleanMessage.isBlank()) {
            throw new IllegalArgumentException("Message is required.");
        }
        if (cleanMessage.length() > 2000) {
            throw new IllegalArgumentException("Message is too long. Please keep it under 2000 characters.");
        }

        Map<String, Object> payload = buildPayload(request, cleanMessage);

        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode() + ".");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("")
                    .trim();
            if (answer.isBlank()) {
                throw new IllegalStateException("OpenAI returned an empty answer.");
            }
            return new AiMessageResponse(answer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI message request was interrupted.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate AI answer.", e);
        }
    }

    private Map<String, Object> buildPayload(AiMessageRequest request, String cleanMessage) {
        return Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", buildMessages(request, cleanMessage)
        );
    }

    private List<Map<String, String>> buildMessages(AiMessageRequest request, String cleanMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", loadChatSystemPrompt()));
        messages.add(Map.of(
                "role", "system",
                "content", "REFERENCE ROUTE-INSTRUCTION SYSTEM PROMPT\n" + loadRouteSystemPrompt()
        ));
        messages.add(Map.of(
                "role", "system",
                "content", "CURRENT GENERATED ROUTE CONTEXT\n" + routeContextJson(request == null ? null : request.routeContext())
        ));

        if (request != null && request.messages() != null) {
            for (AiMessageRequest.ChatMessage historyMessage : request.messages()) {
                String role = normalizeRole(historyMessage == null ? null : historyMessage.role());
                String text = historyMessage == null || historyMessage.text() == null ? "" : historyMessage.text().trim();
                if (role.isBlank() || text.isBlank()) {
                    continue;
                }
                messages.add(Map.of("role", role, "content", text));
            }
        }

        messages.add(Map.of("role", "user", "content", cleanMessage));
        return messages;
    }

    private String routeContextJson(AiMessageRequest.RouteContext routeContext) {
        if (routeContext == null) {
            return "No route has been generated yet.";
        }
        try {
            return objectMapper.writeValueAsString(routeContext);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize current route context.", e);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return switch (role.trim().toLowerCase()) {
            case "user" -> "user";
            case "assistant", "ai" -> "assistant";
            default -> "";
        };
    }

    private String loadRouteSystemPrompt() {
        return loadPrompt(LOCAL_ROUTE_SYSTEM_PROMPT, ROUTE_SYSTEM_PROMPT_RESOURCE, "route instruction system prompt");
    }

    private String loadChatSystemPrompt() {
        return loadPrompt(LOCAL_CHAT_SYSTEM_PROMPT, CHAT_SYSTEM_PROMPT_RESOURCE, "AI message system prompt");
    }

    private String loadPrompt(Path localPath, String resourceName, String description) {
        try {
            if (Files.exists(localPath)) {
                return Files.readString(localPath, StandardCharsets.UTF_8).trim();
            }
            try (InputStream inputStream = AiMessageService.class
                    .getClassLoader()
                    .getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing prompt resource: " + resourceName);
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + description + ".", e);
        }
    }
}
