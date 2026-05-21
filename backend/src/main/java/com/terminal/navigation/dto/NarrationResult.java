package com.terminal.navigation.dto;

import java.util.List;

public record NarrationResult(String summary, List<NarrationInstruction> instructions) {}
