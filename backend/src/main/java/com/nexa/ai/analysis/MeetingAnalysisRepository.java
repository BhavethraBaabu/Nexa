package com.nexa.ai.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingAnalysisRepository extends JpaRepository<MeetingAnalysis, UUID> {

    Optional<MeetingAnalysis> findFirstByMeetingIdOrderByCreatedAtDesc(UUID meetingId);

    @Query("SELECT a FROM MeetingAnalysis a WHERE a.status IN :statuses AND a.createdAt < :before")
    List<MeetingAnalysis> findStale(@Param("statuses") Collection<AnalysisStatus> statuses, @Param("before") Instant before);
}
