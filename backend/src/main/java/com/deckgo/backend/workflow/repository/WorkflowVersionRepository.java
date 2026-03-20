package com.deckgo.backend.workflow.repository;

import com.deckgo.backend.workflow.entity.WorkflowVersionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, UUID> {
    Optional<WorkflowVersionEntity> findTopByProjectIdOrderByVersionNumberDesc(UUID projectId);
}
