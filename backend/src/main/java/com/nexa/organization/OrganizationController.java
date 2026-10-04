package com.nexa.organization;

import com.nexa.common.security.AuthenticatedUser;
import com.nexa.organization.dto.ChangeRoleRequest;
import com.nexa.organization.dto.InvitationResponse;
import com.nexa.organization.dto.InviteMemberRequest;
import com.nexa.organization.dto.MemberResponse;
import com.nexa.organization.dto.OrganizationResponse;
import com.nexa.organization.dto.UpdateOrganizationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Organization and membership endpoints (PRD sections 7.2, 7.3 and 29).
 * Organization management, invitations and member changes are ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final MemberService memberService;
    private final InvitationService invitationService;

    public OrganizationController(OrganizationService organizationService, MemberService memberService,
                                  InvitationService invitationService) {
        this.organizationService = organizationService;
        this.memberService = memberService;
        this.invitationService = invitationService;
    }

    @GetMapping("/current")
    public OrganizationResponse current(@AuthenticationPrincipal AuthenticatedUser current) {
        return organizationService.getCurrent(current);
    }

    @PatchMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationResponse updateCurrent(@AuthenticationPrincipal AuthenticatedUser current,
                                              @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateCurrent(current, request);
    }

    @GetMapping("/members")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser current) {
        return memberService.list(current);
    }

    @PostMapping("/members/invite")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser current,
                                     @Valid @RequestBody InviteMemberRequest request) {
        return invitationService.invite(current, request);
    }

    @PatchMapping("/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse changeRole(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id,
                                     @Valid @RequestBody ChangeRoleRequest request) {
        return memberService.changeRole(current, id, request.role());
    }

    @DeleteMapping("/members/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        memberService.remove(current, id);
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN')")
    public List<InvitationResponse> invitations(@AuthenticationPrincipal AuthenticatedUser current) {
        return invitationService.listPending(current);
    }

    @DeleteMapping("/invitations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable UUID id) {
        invitationService.revoke(current, id);
    }
}
