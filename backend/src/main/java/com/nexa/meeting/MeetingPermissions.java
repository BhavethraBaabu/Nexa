package com.nexa.meeting;

import com.nexa.common.security.AuthenticatedUser;
import com.nexa.user.Role;

/**
 * Meeting access rules (PRD section 7.3). Everyone in the organization can view its meetings;
 * the creator, managers and admins can change, analyze or delete them.
 */
public final class MeetingPermissions {

    private MeetingPermissions() {
    }

    public static boolean canEdit(AuthenticatedUser user, Meeting meeting) {
        return meeting.getOrganizationId().equals(user.organizationId())
                && (meeting.getCreatedBy().equals(user.userId()) || user.role() == Role.ADMIN || user.role() == Role.MANAGER);
    }
}
