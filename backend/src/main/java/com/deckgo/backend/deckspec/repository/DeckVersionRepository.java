package com.deckgo.backend.deckspec.repository;

import com.deckgo.backend.deckspec.entity.DeckVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeckVersionRepository extends JpaRepository<DeckVersionEntity, UUID> {

    List<DeckVersionEntity> findByProjectIdOrderByVersionNumberDesc(UUID projectId);

    Optional<DeckVersionEntity> findTopByProjectIdOrderByVersionNumberDesc(UUID projectId);
}
