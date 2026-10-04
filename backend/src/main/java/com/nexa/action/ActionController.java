package com.nexa.action;

import com.nexa.common.api.PageResponse;
import com.nexa.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** PRD section 29: AI Actions. */
@RestController
@RequestMapping("/api/v1")
public class ActionController {

    private final ActionService service;

    public ActionController(ActionService service) {
        this.service = service;
    }

    public record BatchRequest(@NotEmpty(message = "Select at least one action") @Size(max = 100) List<UUID> ids) {
    }

    @GetMapping("/actions/pending")
    public PageResponse<ActionResponse> pending(@AuthenticationPrincipal AuthenticatedUser current,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return service.list(current, true, page, size);
    }

    @GetMapping("/actions/history")
    public PageResponse<ActionResponse> history(@AuthenticationPrincipal AuthenticatedUser current,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return service.list(current, false, page, size);
    }

    @GetMapping("/meetings/{meetingId}/actions")
    public List<ActionResponse> forMeeting(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID meetingId) {
        return service.forMeeting(current, meetingId);
    }

    @PostMapping("/actions/{id}/approve")
    public ActionResponse approve(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return service.approve(current, List.of(id)).getFirst();
    }

    @PostMapping("/actions/{id}/reject")
    public ActionResponse reject(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return service.reject(current, List.of(id)).getFirst();
    }

    /** "Approve Selected" (PRD section 15). All-or-nothing: if any action can't be approved, none are. */
    @PostMapping("/actions/approve")
    public List<ActionResponse> approveBatch(@AuthenticationPrincipal AuthenticatedUser current, @Valid @RequestBody BatchRequest request) {
        return service.approve(current, request.ids());
    }

    @PostMapping("/actions/reject")
    public List<ActionResponse> rejectBatch(@AuthenticationPrincipal AuthenticatedUser current, @Valid @RequestBody BatchRequest request) {
        return service.reject(current, request.ids());
    }

    @PostMapping("/actions/{id}/execute")
    public ActionResponse execute(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return service.execute(current, id);
    }

    @PostMapping("/actions/{id}/retry")
    public ActionResponse retry(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return service.retry(current, id);
    }
}
