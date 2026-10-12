package de.tebyte.voicedeck.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public final class DeckConfig {
    public enum Effect { NATURAL, DEEP, HIGH, ROBOT, RADIO, ECHO, CUSTOM }
    public enum Output { LOCAL, VOICECHAT, BOTH }
    public static final int SLOT_COUNT = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public volatile boolean enabled = false;
    public volatile Effect effect = Effect.DEEP;
    public volatile Output output = Output.BOTH;
    public volatile double pitch = -5;
    public volatile double mix = 1;
    public volatile double voiceGain = 1;
    public volatile double boardGain = 0.7;
    public volatile boolean sendWhenMuted = false;
    public volatile boolean showHud = true;
    public volatile String[] slots = new String[SLOT_COUNT];

    public void sanitize() {
        if (effect == null) effect = Effect.DEEP;
        if (output == null) output = Output.BOTH;
        pitch = bounded(pitch, -12, 12, -5);
        mix = bounded(mix, 0, 1, 1);
        voiceGain = bounded(voiceGain, 0, 2, 1);
        boardGain = bounded(boardGain, 0, 1, 0.7);
        slots = slots == null ? new String[SLOT_COUNT] : Arrays.copyOf(slots, SLOT_COUNT);
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && (slots[i].contains("/") || slots[i].contains("\\") || slots[i].equals(".."))) slots[i] = null;
        }
    }

    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public static DeckConfig load(Path path) throws IOException {
        if (!Files.exists(path)) return new DeckConfig();
        try {
            DeckConfig config = GSON.fromJson(Files.readString(path), DeckConfig.class);
            if (config == null) throw new IllegalArgumentException("Leere Konfiguration");
            config.sanitize();
            return config;
        } catch (RuntimeException ex) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            return new DeckConfig();
        }
    }

    public synchronized void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(this), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
