package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.dto.NarrationInputStep;
import com.terminal.navigation.dto.NarrationInstruction;
import com.terminal.navigation.dto.NarrationResult;
import com.terminal.navigation.dto.PromptPayload;
import com.terminal.navigation.routing.PassengerProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RouteNarrationService {

    private static final String SYSTEM_PROMPT_RESOURCE = "ai/route-instructions-system-prompt.txt";
    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final boolean useMock;
    private final String mockResponse;
    private final String mockResponseFile;
    private final String promptExportFile;

    public RouteNarrationService(
            ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${OPENAI_MODEL:gpt-4.1-mini}") String model,
            @Value("${OPENAI_USE_MOCK:false}") boolean useMock,
            @Value("${OPENAI_MOCK_RESPONSE:}") String mockResponse,
            @Value("${OPENAI_MOCK_RESPONSE_FILE:}") String mockResponseFile,
            @Value("${OPENAI_PROMPT_EXPORT_FILE:mock/route-instructions.prompt.json}") String promptExportFile
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.useMock = useMock;
        this.mockResponse = mockResponse == null ? "" : mockResponse.trim();
        this.mockResponseFile = mockResponseFile == null ? "" : mockResponseFile.trim();
        this.promptExportFile = promptExportFile == null ? "" : promptExportFile.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public NarrationResult narrate(
            List<NarrationInputStep> inputSteps,
            PassengerProfile profile,
            String fromLabel,
            String toLabel
    ) {
        if (inputSteps == null || inputSteps.isEmpty()) {
            throw new IllegalArgumentException("Input route steps are empty.");
        }

        PromptPayload promptPayload = buildPromptPayloadOrThrow(inputSteps, profile, fromLabel, toLabel);
        exportPromptPayload(promptPayload);

        if (useMock) {
            return mockNarration(inputSteps);
        }

        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured.");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", promptPayload.model());
            payload.put("temperature", 0);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", promptPayload.systemPrompt()),
                    Map.of("role", "user", "content", promptPayload.userPrompt())
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI request failed with status " + response.statusCode() + ".");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");

            return parseNarration(content, inputSteps);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Route instruction generation was interrupted.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate route instructions.", e);
        }
    }

    private PromptPayload buildPromptPayloadOrThrow(
            List<NarrationInputStep> inputSteps,
            PassengerProfile profile,
            String fromLabel,
            String toLabel
    ) {
        try {
            return buildPromptPayload(inputSteps, profile, fromLabel, toLabel);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build OpenAI prompt payload.", e);
        }
    }

    PromptPayload buildPromptPayload(
            List<NarrationInputStep> inputSteps,
            PassengerProfile profile,
            String fromLabel,
            String toLabel
    ) throws Exception {
        List<Map<String, Object>> promptSteps = new ArrayList<>();
        for (int i = 0; i < inputSteps.size(); i++) {
            NarrationInputStep step = inputSteps.get(i);
            Map<String, Object> promptStep = new HashMap<>();
            promptStep.put("stepIndex", i);
            promptStep.put("fromLabel", step.fromLabel());
            promptStep.put("toLabel", step.toLabel());
            promptStep.put("edgeType", step.edgeType());
            promptStep.put("floorFrom", step.floorFrom());
            promptStep.put("floorTo", step.floorTo());
            promptStep.put("fromCheckpointType", step.fromCheckpointType());
            promptStep.put("toCheckpointType", step.toCheckpointType());
            promptStep.put("edgeRelationHint", step.edgeRelationHint());
            promptSteps.add(promptStep);
        }

        String userPrompt = """
                INPUT
                Context:
                - Passenger profile: %s
                - Route from: %s
                - Route to: %s

                InputSteps:
                %s
                """.formatted(
                profile.name(),
                fromLabel,
                toLabel,
                objectMapper.writeValueAsString(promptSteps)
        );

        return new PromptPayload(model, SYSTEM_PROMPT, userPrompt);
    }

    private NarrationResult mockNarration(List<NarrationInputStep> inputSteps) {
        try {
            String content = resolveMockResponse();
            if (content.isBlank()) {
                throw new IllegalStateException("Mock route instruction response is empty.");
            }
            return parseMockNarrationLenient(content, inputSteps);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse mock route instruction response.", e);
        }
    }

    private void exportPromptPayload(PromptPayload promptPayload) {
        if (promptPayload == null || promptExportFile.isBlank()) {
            return;
        }
        try {
            Map<String, Object> snapshot = Map.of(
                    "model", promptPayload.model(),
                    "responseFormat", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content", promptPayload.systemPrompt()),
                            Map.of("role", "user", "content", promptPayload.userPrompt())
                    )
            );
            Path outputPath = Path.of(promptExportFile);
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, objectMapper.writeValueAsString(snapshot), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Best-effort export only.
        }
    }

    private String resolveMockResponse() throws Exception {
        if (!mockResponse.isBlank()) {
            return mockResponse;
        }
        if (!mockResponseFile.isBlank()) {
            return Files.readString(Path.of(mockResponseFile), StandardCharsets.UTF_8).trim();
        }
        return "";
    }

    private NarrationResult parseNarration(
            String content,
            List<NarrationInputStep> inputSteps
    ) throws Exception {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("AI route instruction response is empty.");
        }

        JsonNode ai = objectMapper.readTree(content);
        String summary = ai.path("summary").asText("").trim();
        JsonNode instructionArray = ai.path("instructions");

        if (summary.isBlank() || !instructionArray.isArray() || instructionArray.isEmpty()) {
            throw new IllegalArgumentException("AI route instruction response has invalid summary or instructions.");
        }

        int expectedNext = 0;
        int totalSteps = inputSteps.size();
        List<NarrationInstruction> instructions = new ArrayList<>();

        for (int i = 0; i < instructionArray.size(); i++) {
            JsonNode instructionNode = instructionArray.path(i);
            int fromStepIndex = instructionNode.path("fromStepIndex").asInt(-1);
            int toStepIndex = instructionNode.path("toStepIndex").asInt(-1);
            String text = instructionNode.path("text").asText("").trim();

            if (text.isBlank()) {
                throw new IllegalArgumentException("AI route instruction text is empty.");
            }
            if (fromStepIndex != expectedNext || toStepIndex < fromStepIndex || toStepIndex >= totalSteps) {
                throw new IllegalArgumentException("AI route instruction ranges do not cover the route correctly.");
            }

            List<String> warnings = parseWarnings(instructionNode.path("warnings"));
            instructions.add(new NarrationInstruction(fromStepIndex, toStepIndex, text, List.copyOf(warnings)));
            expectedNext = toStepIndex + 1;
        }

        if (expectedNext != totalSteps) {
            throw new IllegalArgumentException("AI route instruction ranges do not cover the whole route.");
        }

        return new NarrationResult(summary, instructions, "ai");
    }

    private NarrationResult parseMockNarrationLenient(
            String content,
            List<NarrationInputStep> inputSteps
    ) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        String summary = root.path("summary").asText("").trim();
        JsonNode instructionArray = root.path("instructions");

        if (summary.isBlank() || !instructionArray.isArray() || instructionArray.isEmpty()) {
            throw new IllegalArgumentException("Mock route instruction response has invalid summary or instructions.");
        }

        List<NarrationInstruction> instructions = new ArrayList<>();
        for (int i = 0; i < instructionArray.size(); i++) {
            JsonNode instructionNode = instructionArray.path(i);
            int fromStepIndex = instructionNode.path("fromStepIndex").asInt(i);
            int toStepIndex = instructionNode.path("toStepIndex").asInt(fromStepIndex);
            String text = instructionNode.path("text").asText("").trim();
            if (text.isBlank()) {
                continue;
            }

            List<String> warnings = parseWarnings(instructionNode.path("warnings"));
            instructions.add(new NarrationInstruction(fromStepIndex, toStepIndex, text, List.copyOf(warnings)));
        }

        if (instructions.isEmpty()) {
            throw new IllegalArgumentException("Mock route instruction response has no usable instructions.");
        }
        return new NarrationResult(summary, List.copyOf(instructions), "mock");
    }

    private List<String> parseWarnings(JsonNode warningsNode) {
        List<String> warnings = new ArrayList<>();
        if (!warningsNode.isArray()) {
            return warnings;
        }
        for (JsonNode warning : warningsNode) {
            if (!warning.isTextual()) {
                continue;
            }
            String value = warning.asText("").trim();
            if (!value.isBlank() && !warnings.contains(value)) {
                warnings.add(value);
            }
        }
        return warnings;
    }

    private static String loadSystemPrompt() {
        try (InputStream inputStream = RouteNarrationService.class
                .getClassLoader()
                .getResourceAsStream(SYSTEM_PROMPT_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing prompt resource: " + SYSTEM_PROMPT_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + SYSTEM_PROMPT_RESOURCE, e);
        }
    }

}
