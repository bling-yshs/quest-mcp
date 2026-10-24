package com.yshs.questmcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    /**
     * Checks the packaged loader and dependency requirements for the 1.21.1 distribution.
     * @throws IOException if the generated metadata cannot be read
     */
    @Test
    void metadataDeclaresQuestMcp() throws IOException {
        try (InputStream in = ModMetadataTest.class.getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(in, "neoforge.mods.toml is missing");
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("modId=\"questmcp\""), toml);
            assertTrue(toml.contains("modLoader=\"javafml\""), toml);
            assertTrue(toml.contains("loaderVersion=\"[4,)\""), toml);
            assertTrue(toml.contains("versionRange=\"[1.21.1]\""), toml);
            assertTrue(toml.contains("versionRange=\"[21.1.238,21.2)\""), toml);
            assertTrue(toml.contains("versionRange=\"[2101.1.36,2102)\""), toml);
            assertTrue(toml.contains("versionRange=\"[19.50.0.414,20)\""), toml);
        }
    }
}
