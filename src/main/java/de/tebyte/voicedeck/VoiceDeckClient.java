package de.tebyte.voicedeck;

import de.tebyte.voicedeck.audio.*;
import de.tebyte.voicedeck.config.DeckConfig;
import de.tebyte.voicedeck.ui.VoiceDeckScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VoiceDeckClient implements ClientModInitializer {
    public static final String MOD_ID = "voicedeck";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static volatile VoiceDeckClient instance;
    private static final List<Consumer<VoiceDeckClient>> readyActions = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
    private DeckConfig config;
    private AudioEngine engine;
    private SoundLibrary library;
    private Path configFile;
    private KeyMapping menu, toggle, stop;
    private final KeyMapping[] slots = new KeyMapping[DeckConfig.SLOT_COUNT];
    private KeyMapping.Category category;
    private volatile String notice = "";
    private volatile long noticeUntil;

    public static VoiceDeckClient instance() { return instance; }
    public static synchronized void whenReady(Consumer<VoiceDeckClient> action) {
        if (instance == null) readyActions.add(action); else action.accept(instance);
    }
    public DeckConfig config() { return config; }
    public AudioEngine engine() { return engine; }
    public SoundLibrary library() { return library; }
    public String notice() { return System.currentTimeMillis() < noticeUntil ? notice : ""; }

    @Override public void onInitializeClient() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("voicedeck/config.json");
        try { config = DeckConfig.load(configFile); }
        catch (IOException ex) { LOGGER.warn("Could not load VoiceDeck configuration", ex); config = new DeckConfig(); }
        engine = new AudioEngine(config, this::message);
        library = new SoundLibrary(configFile.getParent().resolve("sounds"), config, this::message, this::save);
        library.initialize();
        synchronized (VoiceDeckClient.class) {
            instance = this;
            readyActions.forEach(action -> action.accept(this));
            readyActions.clear();
        }
        category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        menu = key("menu", InputConstants.KEY_B);
        toggle = key("toggle", InputConstants.KEY_R);
        stop = key("stop", InputConstants.KEY_NUMPAD0);
        int[] slotKeys = {InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4,
                InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8};
        for (int i = 0; i < slots.length; i++) slots[i] = key("slot" + (i + 1), slotKeys[i]);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            engine.tick();
            while (menu.consumeClick()) if (client.gui.screen() == null) client.gui.setScreen(new VoiceDeckScreen(null, this));
            while (toggle.consumeClick()) if (client.gui.screen() == null) { config.enabled = !config.enabled; save(); }
            while (stop.consumeClick()) engine.stop();
            for (int i = 0; i < slots.length; i++) while (slots[i].consumeClick()) if (client.gui.screen() == null) playSlot(i, false);
            String next = messages.poll();
            if (next != null) { notice = next; noticeUntil = System.currentTimeMillis() + 8000; }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("VoiceDeck"),
                        button -> client.gui.setScreen(new VoiceDeckScreen(screen, this))).bounds(8, height - 28, 92, 20).build());
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "status"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (!config.showHud || client.gui.screen() != null || client.player == null) return;
            boolean recording = engine.isRecording();
            String sound = engine.playingName();
            if (!config.enabled && sound.isEmpty() && !recording && notice().isEmpty()) return;
            String label = recording ? Component.translatable("voicedeck.recording", engine.recordSecondsLeft()).getString()
                    : config.enabled ? "VoiceDeck · " + Component.translatable("voicedeck.effect." + config.effect.name().toLowerCase(java.util.Locale.ROOT)).getString() : "VoiceDeck";
            if (!sound.isEmpty()) label += " · " + sound;
            int hudWidth = Math.min(client.getWindow().getGuiScaledWidth() - 16, client.font.width(label) + 18);
            context.fill(8, 8, 8 + hudWidth, 27, 0xD9111928);
            context.fill(8, 8, 10, 27, recording ? 0xFFEF6B73 : 0xFF63DFC0);
            context.text(client.font, client.font.plainSubstrByWidth(label, hudWidth - 14), 16, 14, 0xFFE6EDF7, false);
            if (!notice().isEmpty()) context.text(client.font, client.font.plainSubstrByWidth(notice(), client.getWindow().getGuiScaledWidth() - 24), 12, 32, 0xFFDFBC7B, true);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { save(); library.close(); engine.close(); });
        LOGGER.info("VoiceDeck initialized for Minecraft 26.3");
    }

    private KeyMapping key(String name, int keyCode) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.voicedeck." + name, InputConstants.Type.KEYBOARD, keyCode, category));
    }
    public void playSlot(int slot, boolean preview) {
        SoundLibrary.Sound sound = library.slot(slot);
        if (sound == null) message(Component.translatable("voicedeck.empty_slot").getString());
        else engine.play(sound.name(), sound.samples(), preview);
    }
    public void message(String value) { if (messages.size() < 8) messages.offer(value); LOGGER.info("VoiceDeck: {}", value); }
    public synchronized void save() {
        try { config.save(configFile); }
        catch (IOException ex) { LOGGER.warn("Could not save VoiceDeck configuration", ex); message("Einstellungen konnten nicht gespeichert werden"); }
    }
}
