package com.nexa.user;

import com.nexa.audit.AuditAction;
import com.nexa.audit.AuditService;
import com.nexa.common.exception.ResourceNotFoundException;
import com.nexa.common.security.AuthenticatedUser;
import com.nexa.organization.Organization;
import com.nexa.organization.OrganizationRepository;
import com.nexa.user.dto.MeResponse;
import com.nexa.user.dto.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditService auditService;
    private final Clock clock;

    public UserService(UserRepository userRepository, OrganizationRepository organizationRepository,
                       AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(AuthenticatedUser current) {
        return toMe(loadUser(current));
    }

    @Transactional
    public MeResponse updateMe(AuthenticatedUser current, UpdateProfileRequest request) {
        User user = loadUser(current);
        user.rename(request.name().trim(), clock.instant());
        auditService.record(user.getOrganizationId(), user.getId(), AuditAction.USER_PROFILE_UPDATED, "USER", user.getId());
        return toMe(user);
    }

    /** Builds the profile view for a user, e.g. after login. */
    @Transactional(readOnly = true)
    public MeResponse toMe(User user) {
        Organization organization = organizationRepository.findById(user.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", user.getOrganizationId()));
        return MeResponse.from(user, organization);
    }

    private User loadUser(AuthenticatedUser current) {
        return userRepository.findByIdAndOrganizationId(current.userId(), current.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("User", current.userId()));
    }
}
