package com.nexa.task;

import com.nexa.common.api.PageResponse;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.task.dto.TaskResponse;
import com.nexa.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal AuthenticatedUser current,
                                           @RequestParam(defaultValue = "ALL") TaskFilter filter,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.list(current, filter, page, size);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        return service.get(current, id);
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return service.update(current, id, request);
    }
}
