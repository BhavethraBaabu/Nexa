package com.nexa.ai.extraction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schema for the model's output (PRD section 34). Every property is required and nullable
 * values are explicit, so a missing owner is {@code null} rather than an omitted or invented field.
 * Range checks (confidence 0-1, valid dates) are enforced afterwards by {@link ExtractionValidator}.
 * Maps keep insertion order so the request is byte-identical across runs (prompt-cache friendly).
 */
public final class ExtractionSchema {

    private ExtractionSchema() {
    }

    public static Map<String, Object> schema() {
        Map<String, Object> level = ordered("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH"));
        return object(ordered(
                "summary", string(),
                "keyPoints", array(string()),
                "actionItems", array(object(ordered(
                        "title", string(),
                        "description", string(),
                        "ownerName", nullableString(),
                        "deadlineText", nullableString(),
                        "deadline", nullableString(),
                        "priority", level,
                        "confidence", number(),
                        "evidence", string()))),
                "decisions", array(object(ordered(
                        "decision", string(),
                        "context", string(),
                        "confidence", number(),
                        "evidence", string()))),
                "risks", array(object(ordered(
                        "description", string(),
                        "severity", level,
                        "confidence", number(),
                        "evidence", string()))),
                "unresolvedQuestions", array(object(ordered(
                        "question", string(),
                        "confidence", number(),
                        "evidence", string())))));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return ordered("type", "array", "items", items);
    }

    private static Map<String, Object> string() {
        return ordered("type", "string");
    }

    private static Map<String, Object> number() {
        return ordered("type", "number");
    }

    private static Map<String, Object> nullableString() {
        return ordered("type", List.of("string", "null"));
    }

    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
