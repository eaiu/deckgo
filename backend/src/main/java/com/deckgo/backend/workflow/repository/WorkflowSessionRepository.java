package com.deckgo.backend.workflow.repository;

import com.deckgo.backend.workflow.entity.WorkflowSessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowSessionRepository extends JpaRepository<WorkflowSessionEntity, UUID> {
    Optional<WorkflowSessionEntity> findTopByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}
