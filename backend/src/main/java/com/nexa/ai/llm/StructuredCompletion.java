package com.nexa.ai.llm;

/** A model response constrained to a JSON schema. */
public record StructuredCompletion(String json, String model, long inputTokens, long outputTokens) {
}
