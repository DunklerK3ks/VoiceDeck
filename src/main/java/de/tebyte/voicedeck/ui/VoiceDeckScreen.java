package de.tebyte.voicedeck.ui;

import de.tebyte.voicedeck.VoiceDeckClient;
import de.tebyte.voicedeck.audio.AudioEngine;
import de.tebyte.voicedeck.audio.SoundLibrary;
import de.tebyte.voicedeck.config.DeckConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.*;

public final class VoiceDeckScreen extends Screen {
    private final Screen parent;
    private final VoiceDeckClient deck;
    private boolean voiceTab;
    private int selectedSlot, page;
    private int x, y, panelWidth, panelHeight, contentY, contentHeight, columnWidth, rightX;
    private final List<Button> slotButtons = new ArrayList<>();
    private final List<Button> libraryButtons = new ArrayList<>();
    private final Map<DeckConfig.Effect, Button> effectButtons = new EnumMap<>(DeckConfig.Effect.class);
    private List<SoundLibrary.Sound> shownSounds = List.of();
    private Button enabledButton, outputButton, testButton, muteButton;
    private int visibleSounds;

    public VoiceDeckScreen(Screen parent, VoiceDeckClient deck) { super(Component.literal("VoiceDeck")); this.parent = parent; this.deck = deck; }
    private Component t(String key, Object... args) { return Component.translatable("voicedeck." + key, args); }
    private Button button(Component text, int bx, int by, int w, Runnable action) {
        return addRenderableWidget(Button.builder(text, ignored -> action.run()).bounds(bx, by, Math.max(20, w), 20).build());
    }

    @Override protected void init() {
        slotButtons.clear(); libraryButtons.clear(); effectButtons.clear();
        enabledButton = outputButton = testButton = muteButton = null;
        panelWidth = Math.min(610, width - 16);
        panelHeight = Math.min(380, height - 16);
        x = (width - panelWidth) / 2; y = (height - panelHeight) / 2;
        contentY = y + 64; contentHeight = panelHeight - 98;
        columnWidth = (panelWidth - 32) / 2; rightX = x + 20 + columnWidth;
        button(Component.literal("×"), x + panelWidth - 30, y + 8, 20, this::onClose);
        Button boardTab = button(t("tab.soundboard"), x + 12, y + 34, 120, () -> { voiceTab = false; rebuildWidgets(); });
        Button changerTab = button(t("tab.voice"), x + 136, y + 34, 132, () -> { voiceTab = true; rebuildWidgets(); });
        boardTab.active = voiceTab; changerTab.active = !voiceTab;
        if (voiceTab) buildVoice(); else buildBoard();
    }

    private void buildBoard() {
        DeckConfig config = deck.config();
        outputButton = button(outputText(), x + 12, contentY, columnWidth, () -> {
            deck.engine().stop();
            config.output = DeckConfig.Output.values()[(config.output.ordinal() + 1) % DeckConfig.Output.values().length];
            outputButton.setMessage(outputText()); deck.save();
        });
        outputButton.setTooltip(Tooltip.create(t("output.help")));
        addRenderableWidget(new DeckSlider(x + 12, contentY + 24, columnWidth, config.boardGain, 0, 1,
                value -> t("board_volume", (int) Math.round(value * 100)), value -> config.boardGain = value));
        int tileWidth = (columnWidth - 4) / 2;
        int rowHeight = Math.max(18, Math.min(40, (contentHeight - 48) / 4));
        for (int i = 0; i < DeckConfig.SLOT_COUNT; i++) {
            int index = i;
            Button tile = button(Component.empty(), x + 12 + (i % 2) * (tileWidth + 4), contentY + 48 + (i / 2) * rowHeight,
                    tileWidth, () -> { selectedSlot = index; updateSlots(); updateLibrary(); });
            tile.setHeight(Math.max(16, rowHeight - 3));
            tile.setTooltip(Tooltip.create(t("slot.help", i + 1)));
            slotButtons.add(tile);
        }
        visibleSounds = Math.max(2, (contentHeight - 44) / 23);
        for (int i = 0; i < visibleSounds; i++) {
            int offset = i;
            libraryButtons.add(button(Component.empty(), rightX, contentY + 18 + i * 23, columnWidth, () -> {
                int index = page * visibleSounds + offset;
                if (index < shownSounds.size()) { deck.library().assign(selectedSlot, shownSounds.get(index).file()); updateSlots(); }
            }));
        }
        int pagerY = contentY + 20 + visibleSounds * 23;
        button(Component.literal("‹"), rightX, pagerY, 28, () -> { page = Math.max(0, page - 1); updateLibrary(); });
        button(Component.literal("›"), rightX + columnWidth - 28, pagerY, 28, () -> {
            page = Math.min(Math.max(0, (shownSounds.size() - 1) / visibleSounds), page + 1); updateLibrary();
        });
        String[] actions = {"import", "reload", "preview", "play", "stop"};
        Runnable[] callbacks = {() -> minecraft.gui.setScreen(new FilePickerScreen(this, deck, selectedSlot)),
                () -> deck.library().reload(), () -> deck.playSlot(selectedSlot, true),
                () -> deck.playSlot(selectedSlot, false), () -> deck.engine().stop()};
        int w = (panelWidth - 24 - 16) / actions.length;
        for (int i = 0; i < actions.length; i++) button(t(actions[i]), x + 12 + i * (w + 4), y + panelHeight - 28, w, callbacks[i]);
        shownSounds = deck.library().sounds(); updateLibrary(); updateSlots();
    }

    private Component outputText() { return t("output", t("output." + deck.config().output.name().toLowerCase(Locale.ROOT))); }
    private void updateSlots() {
        for (int i = 0; i < slotButtons.size(); i++) {
            SoundLibrary.Sound sound = deck.library().slot(i);
            String name = sound == null ? t("empty").getString() : sound.name();
            Button button = slotButtons.get(i);
            button.setMessage(Component.literal((selectedSlot == i ? "› " : "") + (i + 1) + "  " + font.plainSubstrByWidth(name, button.getWidth() - 34)));
        }
    }
    private void updateLibrary() {
        shownSounds = deck.library().sounds();
        page = Math.min(page, Math.max(0, (shownSounds.size() - 1) / visibleSounds));
        for (int i = 0; i < libraryButtons.size(); i++) {
            int index = page * visibleSounds + i;
            Button button = libraryButtons.get(i);
            button.visible = index < shownSounds.size();
            if (button.visible) {
                SoundLibrary.Sound sound = shownSounds.get(index);
                button.setMessage(Component.literal(font.plainSubstrByWidth(sound.name(), columnWidth - 45) + " · " + String.format(Locale.ROOT, "%.1fs", sound.duration())));
                button.setTooltip(Tooltip.create(t("assign.help", selectedSlot + 1)));
            }
        }
    }

    private void buildVoice() {
        DeckConfig config = deck.config();
        enabledButton = button(t(config.enabled ? "voice.on" : "voice.off"), x + 12, contentY, columnWidth, () -> {
            config.enabled = !config.enabled; deck.save();
        });
        int w = (columnWidth - 8) / 3;
        DeckConfig.Effect[] presets = {DeckConfig.Effect.NATURAL, DeckConfig.Effect.DEEP, DeckConfig.Effect.HIGH,
                DeckConfig.Effect.ROBOT, DeckConfig.Effect.RADIO, DeckConfig.Effect.ECHO};
        for (int i = 0; i < presets.length; i++) {
            DeckConfig.Effect effect = presets[i];
            effectButtons.put(effect, button(t("effect." + effect.name().toLowerCase(Locale.ROOT)), x + 12 + (i % 3) * (w + 4),
                    contentY + 24 + (i / 3) * 23, w, () -> { config.effect = effect; deck.save(); }));
        }
        addRenderableWidget(new DeckSlider(x + 12, contentY + 71, columnWidth, config.pitch, -12, 12,
                value -> t("pitch", String.format(Locale.ROOT, "%+.1f", value)), value -> { config.pitch = value; config.effect = DeckConfig.Effect.CUSTOM; }));
        addRenderableWidget(new DeckSlider(x + 12, contentY + 95, columnWidth, config.mix, 0, 1,
                value -> t("mix", (int) Math.round(value * 100)), value -> config.mix = value));
        addRenderableWidget(new DeckSlider(rightX, contentY, columnWidth, config.voiceGain, 0, 2,
                value -> t("voice_volume", (int) Math.round(value * 100)), value -> config.voiceGain = value));
        testButton = button(t("mic_test"), rightX, contentY + 24, columnWidth, () -> deck.engine().startTest());
        testButton.setTooltip(Tooltip.create(t("mic_test.help")));
        muteButton = button(muteText(), rightX, contentY + 48, columnWidth, () -> {
            config.sendWhenMuted = !config.sendWhenMuted;
            if (!config.sendWhenMuted && deck.engine().connection() == AudioEngine.Connection.MUTED) deck.engine().stop();
            deck.save();
        });
        muteButton.setTooltip(Tooltip.create(t("muted.help")));
        int half = (panelWidth - 28) / 2;
        button(t(config.showHud ? "hud.on" : "hud.off"), x + 12, y + panelHeight - 28, half, () -> {
            config.showHud = !config.showHud; deck.save(); rebuildWidgets();
        });
        button(t("stop"), x + 16 + half, y + panelHeight - 28, half, () -> deck.engine().stop());
    }
    private Component muteText() { return t(deck.config().sendWhenMuted ? "muted.on" : "muted.off"); }

    @Override public void tick() {
        if (!voiceTab) {
            if (shownSounds != deck.library().sounds()) { updateLibrary(); updateSlots(); }
        } else {
            enabledButton.setMessage(t(deck.config().enabled ? "voice.on" : "voice.off"));
            effectButtons.forEach((effect, button) -> button.setMessage(Component.literal((deck.config().effect == effect ? "› " : "") + t("effect." + effect.name().toLowerCase(Locale.ROOT)).getString())));
            muteButton.setMessage(muteText());
            testButton.setMessage(deck.engine().isRecording() ? t("recording", deck.engine().recordSecondsLeft()) : t("mic_test"));
            testButton.active = !deck.engine().isRecording() && deck.engine().connection() == AudioEngine.Connection.CONNECTED;
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xED080D17, 0xF0151F30);
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xFF131D2D);
        context.fill(x, y, x + panelWidth, y + 2, 0xFF63DFC0);
        context.text(font, Component.literal("VOICEDECK"), x + 13, y + 12, 0xFF63DFC0, false);
        String status = t("status." + deck.engine().connection().name().toLowerCase(Locale.ROOT)).getString();
        int statusX = x + 112;
        context.text(font, font.plainSubstrByWidth(status, panelWidth - 152), statusX, y + 12, 0xFFADBBD0, false);
        if (!voiceTab) {
            context.text(font, t("library", selectedSlot + 1), rightX, contentY + 3, 0xFFE6EDF7, false);
            context.centeredText(font, Component.literal((page + 1) + " / " + Math.max(1, (int) Math.ceil(shownSounds.size() / (double) visibleSounds))),
                    rightX + columnWidth / 2, contentY + 26 + visibleSounds * 23, 0xFFADBBD0);
            if (deck.library().busy()) context.text(font, t("loading"), x + 12, y + 26, 0xFFDFBC7B, false);
        } else {
            int meterY = contentY + 78;
            context.fill(rightX, meterY, rightX + columnWidth, meterY + 5, 0xFF263449);
            context.fill(rightX, meterY, rightX + (int) (columnWidth * Math.min(1, deck.engine().level * 4)), meterY + 5, 0xFF63DFC0);
            context.textWithWordWrap(font, t("voice.help"), rightX, meterY + 12, columnWidth, 0xFFADBBD0);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (!deck.notice().isEmpty()) {
            String message = font.plainSubstrByWidth(deck.notice(), width - 16);
            context.fill(4, height - 13, width - 4, height - 1, 0xED080D17);
            context.text(font, message, 8, height - 11, 0xFFDFBC7B, false);
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // This screen paints its own opaque background before the widgets.
    }
    @Override public void onFilesDrop(List<Path> paths) {
        for (int i = 0; i < paths.size() && selectedSlot + i < DeckConfig.SLOT_COUNT; i++) deck.library().importFile(paths.get(i), selectedSlot + i);
    }
    @Override public void removed() { deck.save(); }
    @Override public void onClose() { deck.save(); minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
