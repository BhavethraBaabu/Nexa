package com.nexa.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Tenant-scoped lookup: returns empty if the user belongs to another organization. */
    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<User> findByOrganizationIdAndStatusOrderByNameAsc(UUID organizationId, UserStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, UserStatus status);

    long countByOrganizationIdAndRoleAndStatus(UUID organizationId, Role role, UserStatus status);
}
