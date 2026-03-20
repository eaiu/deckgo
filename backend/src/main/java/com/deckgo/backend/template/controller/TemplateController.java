package com.deckgo.backend.template.controller;

import com.deckgo.backend.template.dto.TemplateSummary;
import com.deckgo.backend.template.service.TemplateCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateCatalogService templateCatalogService;

    public TemplateController(TemplateCatalogService templateCatalogService) {
        this.templateCatalogService = templateCatalogService;
    }

    @GetMapping
    public List<TemplateSummary> listTemplates() {
        return templateCatalogService.listTemplates();
    }
}
