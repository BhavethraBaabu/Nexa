package com.nexa.audit;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Records sensitive actions (PRD sections 4.4 and 38). Entries are written in the caller's
 * transaction, so an audit row exists if and only if the audited change was committed.
 * Metadata must never contain secrets (passwords, tokens).
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbcTemplate, EntityManager entityManager, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID organizationId, UUID userId, AuditAction action, String resourceType, UUID resourceId,
                       Map<String, ?> metadata) {
        // Pending JPA inserts (e.g. a new organization) must hit the database before the
        // audit row that references them.
        entityManager.flush();
        jdbcTemplate.update("""
                        INSERT INTO audit_logs (id, organization_id, user_id, action, resource_type, resource_id, metadata, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                UUID.randomUUID(), organizationId, userId, action.name(), resourceType, resourceId,
                objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata),
                Timestamp.from(clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID organizationId, UUID userId, AuditAction action, String resourceType, UUID resourceId) {
        record(organizationId, userId, action, resourceType, resourceId, Map.of());
    }
}
