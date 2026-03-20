package com.deckgo.backend.artifact.repository;

import com.deckgo.backend.artifact.entity.ArtifactEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, UUID> {
}
