package com.deckgo.backend.render.repository;

import com.deckgo.backend.render.entity.RenderJobEntity;
import com.deckgo.backend.render.enums.RenderJobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenderJobRepository extends JpaRepository<RenderJobEntity, UUID> {

    Optional<RenderJobEntity> findTopByStatusOrderByCreatedAtAsc(RenderJobStatus status);
}
