package com.nexa.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByMeetingIdOrderByPositionAsc(UUID meetingId);

    List<Decision> findTop5ByOrganizationIdOrderByCreatedAtDescPositionAsc(UUID organizationId);

    @Modifying
    @Query("DELETE FROM Decision d WHERE d.meetingId = :meetingId")
    int deleteByMeetingId(@Param("meetingId") UUID meetingId);
}
