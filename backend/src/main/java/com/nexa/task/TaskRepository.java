package com.nexa.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Task> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, TaskStatus status);

    long countByOrganizationIdAndStatusNot(UUID organizationId, TaskStatus status);

    @Query("""
            SELECT count(t) FROM Task t WHERE t.organizationId = :org AND t.deadline < :today
              AND t.status IN (com.nexa.task.TaskStatus.SUGGESTED, com.nexa.task.TaskStatus.OPEN, com.nexa.task.TaskStatus.IN_PROGRESS)
            """)
    long countOverdue(@Param("org") UUID organizationId, @Param("today") java.time.LocalDate today);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.meetingId = :meetingId AND t.status = com.nexa.task.TaskStatus.SUGGESTED")
    int deleteSuggestedForMeeting(@Param("meetingId") UUID meetingId);
}
