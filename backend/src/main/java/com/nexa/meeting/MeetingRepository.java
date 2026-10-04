package com.nexa.meeting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    Optional<Meeting> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Row lock so concurrent "Analyze" clicks cannot start two jobs for the same meeting. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meeting m WHERE m.id = :id AND m.organizationId = :organizationId")
    Optional<Meeting> findForUpdate(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    Page<Meeting> findByOrganizationId(UUID organizationId, Pageable pageable);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndMeetingDateBetween(UUID organizationId, LocalDate from, LocalDate to);

    List<Meeting> findTop5ByOrganizationIdOrderByMeetingDateDescCreatedAtDesc(UUID organizationId);
}
