package com.nexa.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Optional<Integration> findByOrganizationIdAndProvider(UUID organizationId, IntegrationProvider provider);

    List<Integration> findByOrganizationId(UUID organizationId);

    /** Serializes token refreshes: rotating refresh tokens may only be used once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Integration i WHERE i.id = :id")
    Optional<Integration> findForUpdate(@Param("id") UUID id);
}
