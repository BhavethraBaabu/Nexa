package com.nexa.ai.llm;

import java.util.Map;

/**
 * Provider-neutral boundary for model calls. The rest of the AI module depends only on this
 * interface, so the provider can be swapped and tests can use a scripted fake.
 */
public interface LlmClient {

    /**
     * @param jsonSchema JSON Schema the response must conform to
     * @throws LlmException on any failure, with a user-safe message
     */
    StructuredCompletion completeStructured(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema);
}
