package com.nexa.common;

import com.nexa.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthDoesNotExposeComponentDetailsAnonymously() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void metricsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void systemInfoIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("nexa"))
                .andExpect(jsonPath("$.apiVersion").value("v1"));
    }

    @Test
    void protectedEndpointReturnsStandardJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/meetings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/meetings"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(header().string(CorrelationIdFilter.HEADER,
                        matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void echoesSafeClientCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header(CorrelationIdFilter.HEADER, "req-123"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "req-123"));
    }

    @Test
    void replacesUnsafeClientCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header(CorrelationIdFilter.HEADER, "bad id\nINJECTED"))
                .andExpect(header().string(CorrelationIdFilter.HEADER,
                        matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void setsSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void corsAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/system/info")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/system/info")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
