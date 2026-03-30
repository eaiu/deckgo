package com.deckgo.backend.project.service;

import com.deckgo.backend.project.dto.RequirementAnswerPatchRequest;
import com.deckgo.backend.project.dto.RequirementAnswersBatchRequest;
import com.deckgo.backend.project.dto.RequirementConfirmRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.dto.RequirementFormSnapshot;
import java.util.UUID;

public interface ProjectRequirementService {

    RequirementFormSnapshot getRequirementForm(UUID projectId);

    RequirementFormSnapshot submitRequirementAnswers(UUID projectId, RequirementAnswersBatchRequest request);

    RequirementFormSnapshot patchRequirementAnswer(UUID projectId, String questionCode, RequirementAnswerPatchRequest request);

    ProjectStudioSnapshot confirmRequirements(UUID projectId, RequirementConfirmRequest request);
}
