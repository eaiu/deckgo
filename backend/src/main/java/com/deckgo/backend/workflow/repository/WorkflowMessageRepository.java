package com.deckgo.backend.workflow.repository;

import com.deckgo.backend.workflow.entity.WorkflowMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowMessageRepository extends JpaRepository<WorkflowMessageEntity, UUID> {
    List<WorkflowMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
