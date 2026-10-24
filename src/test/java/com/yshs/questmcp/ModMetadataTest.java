package com.yshs.questmcp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    /**
     * Checks the packaged loader and dependency requirements for the 1.20.1 Forge distribution.
     * @throws IOException if the generated metadata cannot be read
     */
    @Test
    void metadataDeclaresQuestMcp() throws IOException {
        try (InputStream in = ModMetadataTest.class.getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(in, "mods.toml is missing");
            String toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(toml.contains("modId=\"questmcp\""), toml);
            assertTrue(toml.contains("modId=\"forge\""), toml);
            assertTrue(toml.contains("mandatory=true"), toml);
            assertTrue(toml.contains("displayTest=\"IGNORE_ALL_VERSION\""), toml);
            assertTrue(toml.contains("modLoader=\"javafml\""), toml);
            assertTrue(toml.contains("loaderVersion=\"[47,)\""), toml);
            assertTrue(toml.contains("versionRange=\"[1.20.1]\""), toml);
            assertTrue(toml.contains("versionRange=\"[47.3.19,48)\""), toml);
            assertTrue(toml.contains("versionRange=\"[2001.3.1,2002)\""), toml);
            assertTrue(toml.contains("versionRange=\"[15.0.0.12,16)\""), toml);
        }
    }
}
