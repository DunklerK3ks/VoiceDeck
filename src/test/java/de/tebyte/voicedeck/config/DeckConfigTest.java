package de.tebyte.voicedeck.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DeckConfigTest {
    @TempDir Path directory;
    @Test void settingsAndSlotsSurviveSaveAndReload() throws IOException {
        Path file = directory.resolve("nested/config.json"); DeckConfig config = new DeckConfig();
        config.enabled = true; config.pitch = 3.5; config.slots[7] = "my sound.wav"; config.save(file);
        DeckConfig restored = DeckConfig.load(file);
        assertTrue(restored.enabled); assertEquals(3.5, restored.pitch); assertEquals("my sound.wav", restored.slots[7]);
        assertFalse(Files.exists(file.resolveSibling("config.json.tmp")));
    }
    @Test void corruptConfigurationIsPreservedAndDefaultsRemainUsable() throws IOException {
        Path file = directory.resolve("config.json"); Files.writeString(file, "{broken");
        assertFalse(DeckConfig.load(file).enabled);
        try (var files = Files.list(directory)) { assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config.json.invalid-"))); }
    }
    @Test void boundsSettingsAndRejectsSlotPathsOutsideSoundDirectory() throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "{\"pitch\":500,\"boardGain\":-9,\"voiceGain\":99,\"effect\":null,\"output\":null,\"slots\":[\"../secret.wav\",\"C:\\\\other.wav\",\"normal.wav\"]}");
        DeckConfig config = DeckConfig.load(file);
        assertEquals(12, config.pitch); assertEquals(0, config.boardGain); assertEquals(2, config.voiceGain);
        assertEquals(DeckConfig.Effect.DEEP, config.effect); assertEquals(DeckConfig.Output.BOTH, config.output);
        assertEquals(8, config.slots.length); assertNull(config.slots[0]); assertNull(config.slots[1]); assertEquals("normal.wav", config.slots[2]);
    }
}
