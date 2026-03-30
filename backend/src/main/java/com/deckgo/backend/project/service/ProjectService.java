package com.deckgo.backend.project.service;

import com.deckgo.backend.project.dto.ProjectCreateRequest;
import com.deckgo.backend.project.dto.ProjectDetailResponse;
import com.deckgo.backend.project.dto.ProjectResponse;
import com.deckgo.backend.project.dto.ProjectUpdateRequest;
import java.util.List;
import java.util.UUID;

public interface ProjectService {

    List<ProjectResponse> listProjects();

    ProjectDetailResponse getProject(UUID projectId);

    ProjectDetailResponse createProject(ProjectCreateRequest request);

    ProjectDetailResponse updateProject(UUID projectId, ProjectUpdateRequest request);

    void deleteProject(UUID projectId);
}
