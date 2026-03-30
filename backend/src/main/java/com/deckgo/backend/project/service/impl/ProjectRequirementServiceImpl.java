package com.deckgo.backend.project.service.impl;

import com.deckgo.backend.common.exception.NotFoundException;
import com.deckgo.backend.project.dto.RequirementAnswerItemRequest;
import com.deckgo.backend.project.dto.RequirementAnswerPatchRequest;
import com.deckgo.backend.project.dto.RequirementAnswersBatchRequest;
import com.deckgo.backend.project.dto.RequirementConfirmRequest;
import com.deckgo.backend.project.mapper.RequirementFormMapper;
import com.deckgo.backend.project.pojo.RequirementFormPO;
import com.deckgo.backend.project.service.ProjectRequirementService;
import com.deckgo.backend.studio.dto.ProjectStudioCommandRequest;
import com.deckgo.backend.studio.dto.ProjectStudioSnapshot;
import com.deckgo.backend.studio.dto.RequirementFormSnapshot;
import com.deckgo.backend.studio.enums.WorkflowCommandType;
import com.deckgo.backend.studio.service.ProjectStudioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectRequirementServiceImpl implements ProjectRequirementService {

    private static final String STATUS_WAITING_USER = "WAITING_USER";

    private final RequirementFormMapper requirementFormMapper;
    private final ProjectStudioService projectStudioService;
    private final ObjectMapper objectMapper;

    public ProjectRequirementServiceImpl(
        RequirementFormMapper requirementFormMapper,
        ProjectStudioService projectStudioService,
        ObjectMapper objectMapper
    ) {
        this.requirementFormMapper = requirementFormMapper;
        this.projectStudioService = projectStudioService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public RequirementFormSnapshot getRequirementForm(UUID projectId) {
        return toSnapshot(findRequirementForm(projectId));
    }

    @Override
    @Transactional
    public RequirementFormSnapshot submitRequirementAnswers(UUID projectId, RequirementAnswersBatchRequest request) {
        RequirementFormPO form = findRequirementForm(projectId);
        ObjectNode answers = mutableAnswers(form.getAnswersJson());
        for (RequirementAnswerItemRequest item : request.answers()) {
            mergeQuestionAnswer(answers, item.questionCode(), item.value());
        }
        persistAnswers(form.getId(), answers);
        return getRequirementForm(projectId);
    }

    @Override
    @Transactional
    public RequirementFormSnapshot patchRequirementAnswer(UUID projectId, String questionCode, RequirementAnswerPatchRequest request) {
        RequirementFormPO form = findRequirementForm(projectId);
        ObjectNode answers = mutableAnswers(form.getAnswersJson());
        mergeQuestionAnswer(answers, questionCode, request.value());
        persistAnswers(form.getId(), answers);
        return getRequirementForm(projectId);
    }

    @Override
    @Transactional
    public ProjectStudioSnapshot confirmRequirements(UUID projectId, RequirementConfirmRequest request) {
        RequirementFormPO form = findRequirementForm(projectId);
        ObjectNode answers = mutableAnswers(form.getAnswersJson());
        List<String> selectedOptionIds = extractSelectedOptionIds(answers);
        String freeformAnswer = resolveFreeformAnswer(answers, request.noteMd());
        if (hasText(request.noteMd())) {
            answers.put("freeformAnswer", request.noteMd().trim());
            persistAnswers(form.getId(), answers);
        }
        return projectStudioService.executeCommand(
            projectId,
            new ProjectStudioCommandRequest(
                WorkflowCommandType.SUBMIT_DISCOVERY,
                "PROJECT",
                null,
                selectedOptionIds,
                freeformAnswer,
                null
            )
        );
    }

    private RequirementFormPO findRequirementForm(UUID projectId) {
        RequirementFormPO form = requirementFormMapper.selectByProjectId(projectId);
        if (form == null) {
            throw new NotFoundException("需求表不存在: " + projectId);
        }
        return form;
    }

    private RequirementFormSnapshot toSnapshot(RequirementFormPO form) {
        return new RequirementFormSnapshot(
            form.getId(),
            form.getStatus(),
            form.getBasedOnOutlineVersionId(),
            form.getSummaryMd(),
            form.getOutlineContextMd(),
            readJson(form.getFixedItemsJson()),
            readJson(form.getInitSearchQueriesJson()),
            readJson(form.getInitSearchResultsJson()),
            readJson(form.getInitCorpusDigestJson()),
            readJson(form.getAiQuestionsJson()),
            readJson(form.getAnswersJson()),
            form.getCreatedAt(),
            form.getUpdatedAt()
        );
    }

    private ObjectNode mutableAnswers(String answersJson) {
        JsonNode existing = readJson(answersJson);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private void mergeQuestionAnswer(ObjectNode answers, String questionCode, JsonNode value) {
        ObjectNode questionAnswers = ensureObject(answers, "questionAnswers");
        questionAnswers.set(questionCode, value == null ? objectMapper.nullNode() : value.deepCopy());
    }

    private void persistAnswers(UUID requirementFormId, ObjectNode answers) {
        requirementFormMapper.updateAnswersJson(
            requirementFormId,
            writeJson(answers),
            STATUS_WAITING_USER,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<String> extractSelectedOptionIds(ObjectNode answers) {
        List<String> selectedOptionIds = new ArrayList<>();
        appendSelections(selectedOptionIds, answers.get("selectedOptionIds"));
        JsonNode questionAnswers = answers.get("questionAnswers");
        if (questionAnswers != null && questionAnswers.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = questionAnswers.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                appendSelections(selectedOptionIds, value);
                if (value != null && value.isObject()) {
                    appendSelections(selectedOptionIds, value.get("selectedOptionIds"));
                    appendSelections(selectedOptionIds, value.get("value"));
                }
            }
        }
        return selectedOptionIds.stream().distinct().toList();
    }

    private void appendSelections(List<String> selections, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    selections.add(item.asText().trim());
                }
            }
            return;
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            selections.add(node.asText().trim());
        }
    }

    private String resolveFreeformAnswer(ObjectNode answers, String noteMd) {
        if (hasText(noteMd)) {
            return noteMd.trim();
        }
        if (answers.path("freeformAnswer").isTextual()) {
            return answers.path("freeformAnswer").asText("").trim();
        }
        JsonNode questionAnswers = answers.get("questionAnswers");
        if (questionAnswers != null && questionAnswers.isObject()) {
            for (JsonNode value : questionAnswers) {
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText().trim();
                }
                if (value != null && value.isObject() && value.path("value").isTextual() && !value.path("value").asText().isBlank()) {
                    return value.path("value").asText().trim();
                }
            }
        }
        return "";
    }

    private ObjectNode ensureObject(ObjectNode root, String fieldName) {
        JsonNode existing = root.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        root.set(fieldName, created);
        return created;
    }

    private JsonNode readJson(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("解析 requirement form JSON 失败", exception);
        }
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 requirement form JSON 失败", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
