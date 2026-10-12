package de.tebyte.voicedeck.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;

public final class AudioFiles {
    public static final long MAX_FILE_BYTES = 24 * 1024 * 1024;
    public record Decoded(short[] samples, int channels, float sampleRate) {}
    @FunctionalInterface public interface Mp3Reader { Decoded read(InputStream stream) throws IOException; }
    private AudioFiles() {}

    public static short[] read(Path path, Mp3Reader mp3) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Datei nicht gefunden");
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("Datei zu groß: maximal 24 MB");
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".mp3")) {
                if (mp3 == null) throw new IOException("MP3 benötigt Simple Voice Chat. Ohne Voice Chat bitte WAV verwenden.");
                try (InputStream stream = Files.newInputStream(path)) {
                    Decoded data = mp3.read(stream);
                    return Pcm.resample(data.samples(), data.channels(), data.sampleRate());
                }
            }
            if (!name.endsWith(".wav")) throw new IOException("Bitte eine WAV- oder MP3-Datei wählen");
            try (AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile())) {
                AudioFormat format = source.getFormat();
                if (format.getChannels() < 1 || format.getChannels() > 32 || format.getSampleRate() < 1000 || format.getSampleRate() > 384000)
                    throw new IOException("Ungültiges WAV-Format");
                AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16,
                        format.getChannels(), format.getChannels() * 2, format.getSampleRate(), false);
                try (AudioInputStream decoded = AudioSystem.getAudioInputStream(pcm, source)) {
                    long maxBytes = Math.min(192L * 1024 * 1024, (long) ((Pcm.MAX_SECONDS + 1) * format.getSampleRate() * format.getChannels() * 2));
                    byte[] data = decoded.readNBytes((int) maxBytes + 1);
                    if (data.length > maxBytes) throw new IOException("Audio zu lang: maximal 120 Sekunden");
                    short[] samples = new short[data.length / 2];
                    for (int i = 0; i < samples.length; i++) samples[i] = (short) ((data[i * 2] & 255) | (data[i * 2 + 1] << 8));
                    return Pcm.resample(samples, format.getChannels(), format.getSampleRate());
                }
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException ex) {
            throw new IOException("Audio kann nicht gelesen werden: " + ex.getMessage(), ex);
        }
    }

    public static void writeWav(Path path, short[] samples) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        AudioFormat format = new AudioFormat(Pcm.SAMPLE_RATE, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(Pcm.bytes(samples)), format, samples.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
