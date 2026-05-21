package com.terminal.navigation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terminal.navigation.dto.AiMessageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiMessageServiceTest {

    @Test
    @DisplayName("rejects blank chat message")
    void rejectsBlankMessage() {
        AiMessageService service = serviceWithoutApiKey();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.answer(new AiMessageRequest("  ", List.of(), null))
        );

        assertTrue(error.getMessage().contains("Message is required"));
    }

    @Test
    @DisplayName("rejects chat message over maximum length")
    void rejectsTooLongMessage() {
        AiMessageService service = serviceWithoutApiKey();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.answer(new AiMessageRequest("x".repeat(2001), List.of(), null))
        );

        assertTrue(error.getMessage().contains("under 2000 characters"));
    }

    @Test
    @DisplayName("requires OpenAI API key for valid chat message")
    void requiresApiKey() {
        AiMessageService service = serviceWithoutApiKey();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.answer(new AiMessageRequest("Where is security?", List.of(), null))
        );

        assertTrue(error.getMessage().contains("OpenAI API key is not configured"));
    }

    private AiMessageService serviceWithoutApiKey() {
        return new AiMessageService(new ObjectMapper(), "", "gpt-4.1-mini");
    }
}
