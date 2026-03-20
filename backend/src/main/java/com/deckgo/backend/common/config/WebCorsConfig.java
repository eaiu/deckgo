package com.deckgo.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final DeckGoProperties deckGoProperties;

    public WebCorsConfig(DeckGoProperties deckGoProperties) {
        this.deckGoProperties = deckGoProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var allowedOrigins = deckGoProperties.getCors().getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION)
                .maxAge(3600);
    }
}
