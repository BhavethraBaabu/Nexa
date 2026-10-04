package com.nexa.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalActionRepository extends JpaRepository<ExternalAction, UUID> {

    Optional<ExternalAction> findByAiActionId(UUID aiActionId);

    List<ExternalAction> findByAiActionIdIn(Collection<UUID> aiActionIds);
}
