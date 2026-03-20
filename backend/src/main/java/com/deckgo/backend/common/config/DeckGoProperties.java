package com.deckgo.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "deckgo")
public class DeckGoProperties {

    private String contractsDir = "../contracts";
    private String rendererBaseUrl = "http://localhost:4301";
    private String artifactsDir = "../var/artifacts";
    private long renderWorkerDelayMs = 5000L;
    private final Ai ai = new Ai();
    private final Cors cors = new Cors();

    public String getContractsDir() {
        return contractsDir;
    }

    public void setContractsDir(String contractsDir) {
        this.contractsDir = contractsDir;
    }

    public String getRendererBaseUrl() {
        return rendererBaseUrl;
    }

    public void setRendererBaseUrl(String rendererBaseUrl) {
        this.rendererBaseUrl = rendererBaseUrl;
    }

    public String getArtifactsDir() {
        return artifactsDir;
    }

    public void setArtifactsDir(String artifactsDir) {
        this.artifactsDir = artifactsDir;
    }

    public long getRenderWorkerDelayMs() {
        return renderWorkerDelayMs;
    }

    public void setRenderWorkerDelayMs(long renderWorkerDelayMs) {
        this.renderWorkerDelayMs = renderWorkerDelayMs;
    }

    public Ai getAi() {
        return ai;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Ai {
        private boolean fallbackEnabled = true;

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
