package com.deckgo.backend.workflow.repository;

import com.deckgo.backend.workflow.entity.WorkflowSessionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowSessionRepository extends JpaRepository<WorkflowSessionEntity, UUID> {
    Optional<WorkflowSessionEntity> findTopByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from WorkflowSessionEntity session where session.id = :sessionId")
    WorkflowSessionEntity findByIdForUpdate(@Param("sessionId") UUID sessionId);
}
