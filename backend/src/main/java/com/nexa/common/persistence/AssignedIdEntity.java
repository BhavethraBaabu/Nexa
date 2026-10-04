package com.nexa.common.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Base for entities whose UUID is assigned by the application. Implementing {@link Persistable}
 * lets Spring Data call {@code persist} instead of a select-then-merge for new rows.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {
    }

    protected void assignNewId() {
        this.id = UUID.randomUUID();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
