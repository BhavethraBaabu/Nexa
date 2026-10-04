package com.nexa.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.nexa.ai.AiProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Claude via the official Anthropic Java SDK, using structured outputs so the response is
 * guaranteed to match the extraction schema (PRD section 4.3: never parse free-form text).
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    /** Server-side refusal fallback: a declined request is retried on Anthropic's recommended model. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AiProperties properties;
    private final AnthropicClient client;

    public AnthropicLlmClient(AiProperties properties) {
        this.properties = properties;
        this.client = properties.configured()
                ? AnthropicOkHttpClient.builder()
                        .apiKey(properties.apiKey())
                        .baseUrl(properties.baseUrl())
                        .timeout(properties.timeout())
                        .maxRetries(2)
                        .build()
                : null;
    }

    @Override
    public StructuredCompletion completeStructured(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        if (client == null) {
            throw new LlmException(LlmException.Code.NOT_CONFIGURED, "LLM_API_KEY is not set");
        }
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        jsonSchema.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxOutputTokens())
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .format(JsonOutputFormat.builder().schema(schema.build()).build())
                        .build())
                .putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        Message response;
        try {
            response = client.messages().create(params);
        } catch (RateLimitException e) {
            throw new LlmException(LlmException.Code.RATE_LIMITED, "rate limited", e);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.error("Anthropic API rejected the configured credentials");
            throw new LlmException(LlmException.Code.AUTHENTICATION, "authentication failed", e);
        } catch (InternalServerException e) {
            throw new LlmException(LlmException.Code.UNAVAILABLE, "provider error " + e.statusCode(), e);
        } catch (AnthropicServiceException e) {
            // 529 overloaded and other statuses not covered above.
            if (e.statusCode() >= 500) {
                throw new LlmException(LlmException.Code.UNAVAILABLE, "provider error " + e.statusCode(), e);
            }
            throw new LlmException(LlmException.Code.FAILED, "request rejected with status " + e.statusCode(), e);
        } catch (AnthropicIoException e) {
            throw new LlmException(LlmException.Code.UNAVAILABLE, "network error", e);
        }

        // Check why generation stopped before reading content.
        StopReason stopReason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new LlmException(LlmException.Code.REFUSED, "model refused");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new LlmException(LlmException.Code.TRUNCATED, "hit max_tokens");
        }

        String json = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining());
        if (json.isBlank()) {
            throw new LlmException(LlmException.Code.INVALID_RESPONSE, "no text content");
        }
        return new StructuredCompletion(json, response.model().asString(),
                response.usage().inputTokens(), response.usage().outputTokens());
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

}
