package com.nexa.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfig {

    /** Base builder for provider clients; each client clones it with its own base URL and timeouts. */
    @Bean
    RestClient.Builder integrationRestClientBuilder() {
        return RestClient.builder().defaultHeader("User-Agent", "Nexa/1.0");
    }
}
