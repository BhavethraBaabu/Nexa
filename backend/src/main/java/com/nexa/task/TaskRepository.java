package com.nexa.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    long countByOrganizationId(UUID organizationId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.meetingId = :meetingId AND t.status = com.nexa.task.TaskStatus.SUGGESTED")
    int deleteSuggestedForMeeting(@Param("meetingId") UUID meetingId);
}
