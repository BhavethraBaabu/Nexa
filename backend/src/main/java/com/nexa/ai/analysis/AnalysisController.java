package com.nexa.ai.analysis;

import com.nexa.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@AuthenticationPrincipal AuthenticatedUser current,
                                                    @PathVariable UUID meetingId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(analysisService.request(current, meetingId));
    }

    @GetMapping("/analysis")
    public AnalysisResponse latest(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID meetingId) {
        return analysisService.latest(current, meetingId);
    }
}
