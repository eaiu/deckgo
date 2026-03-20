package com.deckgo.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.deckgo.backend.common.config.DeckGoProperties;
import org.junit.jupiter.api.Test;

class BackendApplicationTests {

    @Test
    void shouldExposeStableDefaultsForConfigurationProperties() {
        DeckGoProperties properties = new DeckGoProperties();

        assertEquals("../contracts", properties.getContractsDir());
        assertEquals("http://localhost:4301", properties.getRendererBaseUrl());
    }
}
