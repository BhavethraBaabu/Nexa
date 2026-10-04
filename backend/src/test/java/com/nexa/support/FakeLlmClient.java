package com.nexa.support;

import com.nexa.ai.llm.LlmClient;
import com.nexa.ai.llm.LlmException;
import com.nexa.ai.llm.StructuredCompletion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Scripted stand-in for the model: tests queue responses or failures and inspect the prompts sent. */
public class FakeLlmClient implements LlmClient {

    private final Deque<Object> script = new ArrayDeque<>();
    private volatile String lastSystemPrompt;
    private volatile String lastUserPrompt;
    private volatile Map<String, Object> lastSchema;
    private volatile int calls;

    public FakeLlmClient respondWith(String json) {
        script.add(json);
        return this;
    }

    public FakeLlmClient failWith(LlmException.Code code) {
        script.add(new LlmException(code, "scripted failure"));
        return this;
    }

    @Override
    public synchronized StructuredCompletion completeStructured(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        calls++;
        lastSystemPrompt = systemPrompt;
        lastUserPrompt = userPrompt;
        lastSchema = jsonSchema;
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("FakeLlmClient has no scripted response");
        }
        if (next instanceof LlmException e) {
            throw e;
        }
        return new StructuredCompletion((String) next, "fake-model", 1200, 300);
    }

    public String lastSystemPrompt() { return lastSystemPrompt; }
    public String lastUserPrompt() { return lastUserPrompt; }
    public Map<String, Object> lastSchema() { return lastSchema; }
    public int calls() { return calls; }

    void reset() {
        script.clear();
        lastSystemPrompt = null;
        lastUserPrompt = null;
        lastSchema = null;
        calls = 0;
    }
}
