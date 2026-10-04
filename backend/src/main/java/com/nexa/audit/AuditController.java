package com.nexa.audit;

import com.nexa.common.api.PageResponse;
import com.nexa.common.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Audit log for admins (PRD section 47), newest first, scoped to the caller's organization. */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditController(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record AuditEntry(UUID id, Instant timestamp, UUID userId, String userName, String action, String resourceType,
                             UUID resourceId, JsonNode metadata) {
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> list(@AuthenticationPrincipal AuthenticatedUser current,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        int pageSize = Math.clamp(size, 1, 100);
        int pageIndex = Math.max(page, 0);
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE organization_id = ?", Long.class, current.organizationId());
        List<AuditEntry> entries = jdbc.query("""
                        SELECT a.id, a.created_at, a.user_id, u.name, a.action, a.resource_type, a.resource_id, a.metadata::text
                        FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id
                        WHERE a.organization_id = ?
                        ORDER BY a.created_at DESC, a.id
                        LIMIT ? OFFSET ?
                        """,
                (rs, i) -> new AuditEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class),
                        objectMapper.readTree(rs.getString(8))),
                current.organizationId(), pageSize, (long) pageIndex * pageSize);
        return new PageResponse<>(entries, pageIndex, pageSize, total, (int) ((total + pageSize - 1) / pageSize));
    }
}
