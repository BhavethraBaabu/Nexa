package com.nexa.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MeetingQuestionRepository extends JpaRepository<MeetingQuestion, UUID> {

    List<MeetingQuestion> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    @Modifying
    @Query("DELETE FROM MeetingQuestion q WHERE q.meetingId = :meetingId")
    int deleteByMeetingId(@Param("meetingId") UUID meetingId);
}
