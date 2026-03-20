package com.deckgo.backend.ai.service;

import com.deckgo.backend.template.service.TemplateCatalogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TemplateCatalogTools {

    private final TemplateCatalogService templateCatalogService;
    private final ObjectMapper objectMapper;

    public TemplateCatalogTools(TemplateCatalogService templateCatalogService, ObjectMapper objectMapper) {
        this.templateCatalogService = templateCatalogService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "读取当前系统可用的模板列表、主题色和适配的 slide kind")
    public String getTemplateCatalog() {
        try {
            return objectMapper.writeValueAsString(templateCatalogService.listTemplates());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化模板目录失败", exception);
        }
    }
}
