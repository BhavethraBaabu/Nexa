package com.nexa.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "nexa.ai")
public record AiProperties(
        /** Anthropic API key (LLM_API_KEY). Analysis fails with a clear message when it is not set. */
        String apiKey,
        /** API endpoint; overridable for proxies and tests. */
        @NotBlank String baseUrl,
        @NotBlank String model,
        /** low | medium | high | xhigh | max. Higher is more thorough and slower. */
        @NotBlank String effort,
        @Min(1024) long maxOutputTokens,
        @NotNull Duration timeout,
        /** Transcripts longer than this are rejected at upload rather than silently truncated. */
        @Min(1000) int maxTranscriptChars,
        /** Analyses still PROCESSING after this long (e.g. the server restarted mid-job) are marked failed. */
        @NotNull Duration staleAfter
) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
