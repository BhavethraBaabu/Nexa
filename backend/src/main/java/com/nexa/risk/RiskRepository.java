package com.nexa.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

    List<Risk> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    @Modifying
    @Query("DELETE FROM Risk r WHERE r.meetingId = :meetingId")
    int deleteByMeetingId(@Param("meetingId") UUID meetingId);
}
