package com.nexa.action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiActionRepository extends JpaRepository<AiAction, UUID> {

    Optional<AiAction> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AiAction> findByIdInAndOrganizationId(Collection<UUID> ids, UUID organizationId);

    List<AiAction> findByMeetingIdOrderByCreatedAtAsc(UUID meetingId);

    List<AiAction> findByTaskIdIn(Collection<UUID> taskIds);

    Page<AiAction> findByOrganizationIdAndStatusIn(UUID organizationId, Collection<ActionStatus> statuses, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByOrganizationIdAndStatus(UUID organizationId, ActionStatus status);

    /**
     * Atomically claims an approved action for execution. Exactly one caller wins, so a double
     * click, a scheduler tick and a manual "execute" can never run the same action twice.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AiAction a SET a.status = com.nexa.action.ActionStatus.EXECUTING, a.attempts = a.attempts + 1,
                   a.updatedAt = :now
            WHERE a.id = :id AND a.status = com.nexa.action.ActionStatus.APPROVED
            """)
    int claim(@Param("id") UUID id, @Param("now") Instant now);

    @Query("SELECT a.id FROM AiAction a WHERE a.status = com.nexa.action.ActionStatus.APPROVED AND a.nextAttemptAt <= :now")
    List<UUID> findDue(@Param("now") Instant now);

    /** Executions that never finished (e.g. the server stopped mid-call); safe to retry because handlers are idempotent. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE AiAction a SET a.status = com.nexa.action.ActionStatus.APPROVED, a.nextAttemptAt = :now, a.updatedAt = :now
            WHERE a.status = com.nexa.action.ActionStatus.EXECUTING AND a.updatedAt < :before
            """)
    int releaseStale(@Param("before") Instant before, @Param("now") Instant now);

    /** Pending suggestions are replaced when a meeting is re-analyzed; anything a person acted on stays. */
    @Modifying
    @Query("DELETE FROM AiAction a WHERE a.meetingId = :meetingId AND a.status = com.nexa.action.ActionStatus.PENDING")
    int deletePendingForMeeting(@Param("meetingId") UUID meetingId);
}
