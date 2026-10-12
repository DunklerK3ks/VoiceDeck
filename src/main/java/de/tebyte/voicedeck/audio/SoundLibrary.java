package de.tebyte.voicedeck.audio;

import de.tebyte.voicedeck.config.DeckConfig;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SoundLibrary implements AutoCloseable {
    public record Sound(String file, String name, short[] samples) {
        public double duration() { return samples.length / (double) Pcm.SAMPLE_RATE; }
    }
    private final Path directory;
    private final DeckConfig config;
    private final Consumer<String> messages;
    private final Runnable save;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VoiceDeck-Import"); thread.setDaemon(true); return thread;
    });
    private volatile List<Sound> sounds = List.of();
    private volatile AudioFiles.Mp3Reader mp3;
    private volatile boolean busy;

    public SoundLibrary(Path directory, DeckConfig config, Consumer<String> messages, Runnable save) {
        this.directory = directory;
        this.config = config;
        this.messages = messages;
        this.save = save;
    }
    public Path directory() { return directory; }
    public List<Sound> sounds() { return sounds; }
    public boolean busy() { return busy; }
    public boolean supportsMp3() { return mp3 != null; }
    public void mp3Reader(AudioFiles.Mp3Reader reader) { mp3 = reader; reload(); }

    public void initialize() {
        worker.execute(() -> {
            try {
                Files.createDirectories(directory);
                for (int i = 0; i < DemoSounds.NAMES.length; i++) {
                    String filename = (i + 1) + "-" + DemoSounds.NAMES[i].replace(' ', '-') + ".wav";
                    Path file = directory.resolve(filename);
                    if (!Files.exists(file)) AudioFiles.writeWav(file, DemoSounds.create(i));
                    if (config.slots[i] == null) config.slots[i] = filename;
                }
                save.run();
                scan();
            } catch (IOException ex) { messages.accept("Soundordner: " + ex.getMessage()); }
        });
    }

    public Sound slot(int index) {
        if (index < 0 || index >= DeckConfig.SLOT_COUNT) return null;
        String file = config.slots[index];
        return sounds.stream().filter(sound -> sound.file().equals(file)).findFirst().orElse(null);
    }
    public void assign(int slot, String filename) {
        if (slot >= 0 && slot < DeckConfig.SLOT_COUNT) { config.slots[slot] = filename; save.run(); }
    }
    public void reload() { worker.execute(this::scan); }

    private void scan() {
        busy = true;
        List<Sound> result = new ArrayList<>();
        long totalSamples = 0;
        try (var files = Files.list(directory)) {
            List<Path> paths = files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".wav") || (name.endsWith(".mp3") && mp3 != null);
            }).sorted().toList();
            for (Path path : paths) {
                if (result.size() >= 64 || totalSamples >= 24L * Pcm.SAMPLE_RATE * 60) {
                    messages.accept("Bibliothekslimit erreicht: 64 Sounds / 24 Minuten Audio"); break;
                }
                try {
                    short[] samples = AudioFiles.read(path, mp3);
                    totalSamples += samples.length;
                    String file = path.getFileName().toString();
                    String name = file.substring(0, file.lastIndexOf('.')).replace('-', ' ');
                    for (int i = 0; i < DemoSounds.NAMES.length; i++) {
                        if (file.equals((i + 1) + "-" + DemoSounds.NAMES[i].replace(' ', '-') + ".wav")) name = DemoSounds.NAMES[i];
                    }
                    result.add(new Sound(file, name, samples));
                } catch (IOException ex) { messages.accept(path.getFileName() + ": " + ex.getMessage()); }
            }
            sounds = List.copyOf(result);
        } catch (IOException ex) { messages.accept("Soundordner: " + ex.getMessage()); }
        finally { busy = false; }
    }

    public void importFile(Path source, int slot) {
        worker.execute(() -> {
            busy = true;
            try {
                AudioFiles.read(source, mp3); // Validate before copying into the library.
                String filename = source.getFileName().toString();
                Path destination = directory.resolve(filename);
                if (!source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                    String stem = filename.substring(0, filename.lastIndexOf('.'));
                    String extension = filename.substring(filename.lastIndexOf('.'));
                    int suffix = 2;
                    while (Files.exists(destination)) destination = directory.resolve(stem + "-" + suffix++ + extension);
                    Files.copy(source, destination);
                }
                assign(slot, destination.getFileName().toString());
                scan();
                messages.accept("Importiert: " + destination.getFileName());
            } catch (IOException | RuntimeException ex) { messages.accept("Import fehlgeschlagen: " + ex.getMessage()); }
            finally { busy = false; }
        });
    }
    @Override public void close() { worker.shutdownNow(); }
}
