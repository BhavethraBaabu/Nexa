package com.nexa.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "nexa.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
