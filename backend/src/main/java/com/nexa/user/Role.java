package com.nexa.user;

/**
 * Organization roles (PRD section 7.3). Ordered from most to least privileged;
 * the hierarchy ADMIN > MANAGER > MEMBER is enforced by Spring Security.
 */
public enum Role {
    ADMIN,
    MANAGER,
    MEMBER;

    public String authority() {
        return "ROLE_" + name();
    }
}
