package com.nexa.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            SELECT i FROM Invitation i
            WHERE i.organizationId = :organizationId
              AND i.acceptedAt IS NULL AND i.revokedAt IS NULL AND i.expiresAt > :now
            ORDER BY i.createdAt DESC
            """)
    List<Invitation> findPending(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Invitation i SET i.revokedAt = :now
            WHERE i.organizationId = :organizationId AND i.email = :email
              AND i.acceptedAt IS NULL AND i.revokedAt IS NULL
            """)
    int revokePendingForEmail(@Param("organizationId") UUID organizationId, @Param("email") String email,
                              @Param("now") Instant now);
}
