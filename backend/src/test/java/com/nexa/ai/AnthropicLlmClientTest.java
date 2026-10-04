package com.nexa.ai;

import com.nexa.ai.llm.AnthropicLlmClient;
import com.nexa.ai.llm.LlmException;
import com.nexa.ai.llm.StructuredCompletion;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the real SDK client against a local stub of the Messages API. No network access. */
class AnthropicLlmClientTest {

    private static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties", Map.of(), "required", List.of(),
            "additionalProperties", false);

    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<Map<String, List<String>>> requestHeaders = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestHeaders.set(exchange.getRequestHeaders());
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("retry-after-ms", "0");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsStructuredOutputRequestAndReturnsJson() {
        responseBody = message("{\"summary\":\"ok\"}", "end_turn");

        StructuredCompletion completion = client("sk-test").completeStructured("system prompt", "user prompt", SCHEMA);

        assertThat(completion.json()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(completion.model()).isEqualTo("claude-opus-5-5");
        assertThat(completion.inputTokens()).isEqualTo(1500);
        assertThat(completion.outputTokens()).isEqualTo(220);

        JsonNode body = json.readTree(requestBody.get());
        assertThat(body.get("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(body.get("max_tokens").asLong()).isEqualTo(16000);
        assertThat(body.get("system").asString()).isEqualTo("system prompt");
        assertThat(body.get("messages").get(0).get("content").asString()).isEqualTo("user prompt");
        assertThat(body.get("output_config").get("effort").asString()).isEqualTo("medium");
        assertThat(body.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
        assertThat(body.get("output_config").get("format").get("schema").get("additionalProperties").asBoolean()).isFalse();
        assertThat(body.get("fallbacks").asString()).isEqualTo("default");
        assertThat(body.has("thinking")).isFalse();
        assertThat(requestHeaders.get().get("Anthropic-beta")).contains("server-side-fallback-2026-07-01");
        assertThat(requestHeaders.get().get("X-api-key")).containsExactly("sk-test");
    }

    @Test
    void refusalIsReportedNotParsed() {
        responseBody = message("", "refusal");
        assertCode(LlmException.Code.REFUSED);
    }

    @Test
    void truncatedOutputIsReported() {
        responseBody = message("{\"summary\":", "max_tokens");
        assertCode(LlmException.Code.TRUNCATED);
    }

    @Test
    void rateLimitIsRetryable() {
        status = 429;
        responseBody = error("rate_limit_error");
        assertCode(LlmException.Code.RATE_LIMITED);
        assertThat(requests.get()).isEqualTo(3); // The SDK retried twice before giving up.
    }

    @Test
    void badCredentialsAreNotRetryable() {
        status = 401;
        responseBody = error("authentication_error");
        assertCode(LlmException.Code.AUTHENTICATION);
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void overloadedProviderIsUnavailable() {
        status = 529;
        responseBody = error("overloaded_error");
        assertCode(LlmException.Code.UNAVAILABLE);
    }

    @Test
    void missingApiKeyFailsWithoutAnyRequest() {
        assertThatThrownBy(() -> client("").completeStructured("s", "u", SCHEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.code()).isEqualTo(LlmException.Code.NOT_CONFIGURED));
        assertThat(requests.get()).isZero();
    }

    private void assertCode(LlmException.Code code) {
        assertThatThrownBy(() -> client("sk-test").completeStructured("s", "u", SCHEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.userMessage()).isNotBlank();
                });
    }

    private AnthropicLlmClient client(String apiKey) {
        return new AnthropicLlmClient(new AiProperties(apiKey, "http://127.0.0.1:" + server.getAddress().getPort(),
                "claude-opus-5-5", "medium", 16000, Duration.ofSeconds(5), 300_000, Duration.ofMinutes(15)));
    }

    private static String message(String text, String stopReason) {
        return """
                {"id": "msg_test", "type": "message", "role": "assistant", "model": "claude-opus-5-5",
                 "content": [{"type": "text", "text": %s}], "stop_reason": "%s", "stop_sequence": null,
                 "usage": {"input_tokens": 1500, "output_tokens": 220}}
                """.formatted(JsonMapper.builder().build().writeValueAsString(text), stopReason);
    }

    private static String error(String type) {
        return "{\"type\": \"error\", \"error\": {\"type\": \"" + type + "\", \"message\": \"stub\"}}";
    }
}
