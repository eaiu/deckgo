package com.deckgo.backend.artifact.controller;

import com.deckgo.backend.artifact.entity.ArtifactEntity;
import com.deckgo.backend.artifact.service.ArtifactService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping("/{artifactId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID artifactId) {
        ArtifactEntity artifact = artifactService.getArtifact(artifactId);
        Resource resource = artifactService.loadAsResource(artifact);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.getMediaType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.getFilename() + "\"")
            .body(resource);
    }
}
