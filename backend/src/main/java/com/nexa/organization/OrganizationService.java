package com.nexa.organization;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.organization.dto.OrganizationResponse;
import com.nexa.organization.dto.UpdateOrganizationRequest;
import com.nexa.user.UserRepository;
import com.nexa.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository,
                               AuditService auditService, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getCurrent(AuthenticatedUser current) {
        return toResponse(load(current));
    }

    @Transactional
    public OrganizationResponse updateCurrent(AuthenticatedUser current, UpdateOrganizationRequest request) {
        Organization organization = load(current);
        String previousName = organization.getName();
        organization.rename(request.name().trim(), clock.instant());
        auditService.record(organization.getId(), current.userId(), AuditAction.ORGANIZATION_UPDATED,
                "ORGANIZATION", organization.getId(), Map.of("previousName", previousName, "name", organization.getName()));
        return toResponse(organization);
    }

    private Organization load(AuthenticatedUser current) {
        return organizationRepository.findById(current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", current.organizationId()));
    }

    private OrganizationResponse toResponse(Organization organization) {
        long members = userRepository.countByOrganizationIdAndStatus(organization.getId(), UserStatus.ACTIVE);
        return new OrganizationResponse(organization.getId(), organization.getName(), members, organization.getCreatedAt());
    }
}
