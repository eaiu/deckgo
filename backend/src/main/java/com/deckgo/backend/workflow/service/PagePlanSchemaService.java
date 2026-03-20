package com.deckgo.backend.workflow.service;

import com.deckgo.backend.common.config.DeckGoProperties;
import com.deckgo.backend.common.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PagePlanSchemaService {

    private final ObjectMapper objectMapper;
    private final Path schemaPath;
    private JsonSchema cachedSchema;

    public PagePlanSchemaService(ObjectMapper objectMapper, DeckGoProperties properties) {
        this.objectMapper = objectMapper;
        this.schemaPath = Path.of(properties.getContractsDir(), "pageplan", "v1", "page-plan.schema.json").normalize();
    }

    public void validate(JsonNode pagePlan) {
        Set<ValidationMessage> messages = schema().validate(pagePlan);
        if (!messages.isEmpty()) {
            throw new ValidationException(
                "PagePlan 校验失败",
                messages.stream().map(ValidationMessage::getMessage).sorted().toList()
            );
        }
    }

    private JsonSchema schema() {
        if (cachedSchema == null) {
            try {
                JsonNode schemaNode = objectMapper.readTree(Files.readString(schemaPath));
                cachedSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
            } catch (IOException exception) {
                throw new IllegalStateException("读取 PagePlan Schema 失败: " + schemaPath, exception);
            }
        }
        return cachedSchema;
    }
}
