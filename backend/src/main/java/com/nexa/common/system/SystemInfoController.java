package com.nexa.common.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-sensitive service identity endpoint used by the frontend to confirm the API is reachable.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final String applicationName;

    public SystemInfoController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/info")
    public SystemInfoResponse info() {
        return new SystemInfoResponse(applicationName, "v1", "Where meetings become momentum.");
    }

    public record SystemInfoResponse(String name, String apiVersion, String tagline) {
    }
}
