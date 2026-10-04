package com.nexa.organization;

import com.nexa.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A tenant. Every organization-owned row references one of these (PRD section 7.2).
 */
@Entity
@Table(name = "organizations")
public class Organization extends AssignedIdEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    public static Organization create(String name, Instant now) {
        Organization org = new Organization();
        org.assignNewId();
        org.name = name;
        org.createdAt = now;
        org.updatedAt = now;
        return org;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
