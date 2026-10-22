package com.yshs.questmcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    @Test
    void metadataDeclaresQuestMcp() throws IOException {
        try (InputStream in = ModMetadataTest.class.getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(in, "neoforge.mods.toml is missing");
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("modId=\"questmcp\""), toml);
        }
    }
}
