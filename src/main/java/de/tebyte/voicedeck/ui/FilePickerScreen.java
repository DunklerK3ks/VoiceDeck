package de.tebyte.voicedeck.ui;

import de.tebyte.voicedeck.VoiceDeckClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** In-game file browser: files never need to be prepared outside Minecraft. */
final class FilePickerScreen extends Screen {
    private final Screen parent;
    private final VoiceDeckClient deck;
    private final int slot;
    private Path directory;
    private List<Path> entries = List.of();
    private int page, visibleRows, x, y, w, h;
    private String error = "";
    private boolean loading;
    private long request;
    private EditBox location;
    private final List<Button> rows = new ArrayList<>();

    FilePickerScreen(Screen parent, VoiceDeckClient deck, int slot) {
        super(Component.translatable("voicedeck.import_title")); this.parent = parent; this.deck = deck; this.slot = slot;
        Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
        directory = Files.isDirectory(downloads) ? downloads : Path.of(System.getProperty("user.home"));
    }
    private Component t(String key) { return Component.translatable("voicedeck." + key); }
    private Button button(Component text, int bx, int by, int width, Runnable action) {
        return addRenderableWidget(Button.builder(text, ignored -> action.run()).bounds(bx, by, width, 20).build());
    }
    @Override protected void init() {
        rows.clear();
        w = Math.min(600, width - 16); h = Math.min(370, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        location = new EditBox(font, x + 12, y + 30, w - 92, 20, t("path"));
        location.setMaxLength(4096); location.setValue(directory.toString()); addRenderableWidget(location);
        button(t("go"), x + w - 76, y + 30, 64, this::go);
        int navWidth = (w - 32) / 3;
        button(t("parent"), x + 12, y + 54, navWidth, () -> { if (directory.getParent() != null) navigate(directory.getParent()); });
        button(t("home"), x + 16 + navWidth, y + 54, navWidth, () -> navigate(Path.of(System.getProperty("user.home"))));
        button(t("sound_folder"), x + 20 + navWidth * 2, y + 54, navWidth, () -> navigate(deck.library().directory()));
        visibleRows = Math.max(2, (h - 122) / 23);
        for (int i = 0; i < visibleRows; i++) {
            int offset = i;
            rows.add(button(Component.empty(), x + 12, y + 79 + i * 23, w - 24, () -> {
                int index = page * visibleRows + offset;
                if (index < entries.size()) choose(entries.get(index));
            }));
        }
        button(Component.literal("‹"), x + 12, y + h - 28, 32, () -> { page = Math.max(0, page - 1); updateRows(); });
        button(Component.literal("›"), x + 48, y + h - 28, 32, () -> { page = Math.min(Math.max(0, (entries.size() - 1) / visibleRows), page + 1); updateRows(); });
        button(t("back"), x + w - 108, y + h - 28, 96, this::onClose);
        navigate(directory);
    }
    private void go() {
        try {
            String input = location.getValue().trim();
            if (input.startsWith("\"") && input.endsWith("\"")) input = input.substring(1, input.length() - 1);
            Path path = Path.of(input);
            if (!path.isAbsolute()) path = directory.resolve(path);
            choose(path.normalize());
        } catch (RuntimeException ex) { error = ex.getMessage(); }
    }
    private void choose(Path path) {
        if (Files.isDirectory(path)) navigate(path);
        else { deck.library().importFile(path, slot); onClose(); }
    }
    private void navigate(Path path) {
        directory = path.toAbsolutePath().normalize(); page = 0; loading = true; error = "";
        location.setValue(directory.toString());
        entries = List.of(); updateRows();
        long ticket = ++request;
        Path requestedDirectory = directory;
        CompletableFuture.supplyAsync(() -> {
            try (var stream = Files.list(requestedDirectory)) {
                return stream.filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return Files.isDirectory(p) || name.endsWith(".wav") || name.endsWith(".mp3");
                }).sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p)).thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
            } catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
        }).whenComplete((result, failure) -> minecraft.execute(() -> {
            if (ticket != request || minecraft.gui.screen() != this) return;
            loading = false;
            if (failure != null) error = t("folder_error").getString(); else entries = result;
            updateRows();
        }));
    }
    private void updateRows() {
        for (int i = 0; i < rows.size(); i++) {
            int index = page * visibleRows + i; Button row = rows.get(i);
            row.visible = index < entries.size();
            if (row.visible) {
                Path path = entries.get(index);
                row.setMessage(Component.literal((Files.isDirectory(path) ? "[+] " : "♪ ") + font.plainSubstrByWidth(path.getFileName().toString(), w - 56)));
            }
        }
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) { go(); return true; }
        return super.keyPressed(event);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0xF0080D17, 0xF0151F30);
        context.fill(x, y, x + w, y + h, 0xFF131D2D); context.fill(x, y, x + w, y + 2, 0xFF63DFC0);
        context.text(font, title, x + 12, y + 12, 0xFF63DFC0, false);
        context.text(font, Component.literal(loading ? t("loading").getString() : !error.isEmpty() ? error : t("import_help").getString()), x + 88, y + h - 22, 0xFFADBBD0, false);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    @Override public void onFilesDrop(List<Path> paths) { if (!paths.isEmpty()) { deck.library().importFile(paths.getFirst(), slot); onClose(); } }
    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Avoid applying the vanilla blur after painting the custom labels.
    }
    @Override public void onClose() { request++; minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
