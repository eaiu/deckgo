package com.deckgo.backend.deckspec.service;

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
public class DeckSpecSchemaService {

    private final ObjectMapper objectMapper;
    private final Path schemaPath;
    private JsonSchema cachedSchema;

    public DeckSpecSchemaService(ObjectMapper objectMapper, DeckGoProperties properties) {
        this.objectMapper = objectMapper;
        this.schemaPath = Path.of(properties.getContractsDir(), "deckspec", "v1", "deckspec.schema.json").normalize();
    }

    public void validate(JsonNode deckSpec) {
        Set<ValidationMessage> messages = schema().validate(deckSpec);
        if (!messages.isEmpty()) {
            throw new ValidationException(
                "DeckSpec 校验失败",
                messages.stream().map(ValidationMessage::getMessage).sorted().toList()
            );
        }
    }

    public JsonNode loadSampleDeck() {
        Path samplePath = schemaPath.getParent().getParent().getParent().resolve("examples").resolve("sample-deck.json").normalize();
        try {
            return objectMapper.readTree(Files.readString(samplePath));
        } catch (IOException exception) {
            throw new IllegalStateException("读取示例 DeckSpec 失败: " + samplePath, exception);
        }
    }

    private JsonSchema schema() {
        if (cachedSchema == null) {
            try {
                JsonNode schemaNode = objectMapper.readTree(Files.readString(schemaPath));
                cachedSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
            } catch (IOException exception) {
                throw new IllegalStateException("读取 DeckSpec Schema 失败: " + schemaPath, exception);
            }
        }
        return cachedSchema;
    }
}
