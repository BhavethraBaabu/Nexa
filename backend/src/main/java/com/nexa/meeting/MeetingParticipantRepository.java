package com.nexa.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {

    List<MeetingParticipant> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    @Query("SELECT p.meetingId, count(p) FROM MeetingParticipant p WHERE p.meetingId IN :ids GROUP BY p.meetingId")
    List<Object[]> countByMeetingIds(@Param("ids") Collection<UUID> meetingIds);

    @Modifying
    @Query("DELETE FROM MeetingParticipant p WHERE p.meetingId = :meetingId")
    void deleteByMeetingId(@Param("meetingId") UUID meetingId);
}
