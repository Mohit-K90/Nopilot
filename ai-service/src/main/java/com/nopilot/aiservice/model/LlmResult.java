package com.nopilot.aiservice.model;  public record LlmResult(String text, TokenUsage usage, String model, long durationMs) { }
