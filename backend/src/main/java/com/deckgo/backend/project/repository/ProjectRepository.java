package com.deckgo.backend.project.repository;

import com.deckgo.backend.project.entity.ProjectEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
}
