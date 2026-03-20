package com.deckgo.backend.workflow.repository;

import com.deckgo.backend.workflow.entity.WorkflowPageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowPageRepository extends JpaRepository<WorkflowPageEntity, UUID> {
    List<WorkflowPageEntity> findByWorkflowVersionIdOrderByOrderIndexAsc(UUID workflowVersionId);
}
