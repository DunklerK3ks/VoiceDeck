package de.tebyte.voicedeck.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class AudioFilesTest {
    @TempDir Path directory;
    @Test void generatedWavRoundTripsWithoutSampleChanges() throws IOException {
        short[] samples = DemoSounds.create(0); Path file = directory.resolve("demo.wav");
        AudioFiles.writeWav(file, samples);
        assertArrayEquals(samples, AudioFiles.read(file, null));
    }
    @Test void rejectsUnknownCorruptMissingAndEmptyFiles() throws IOException {
        Path text = directory.resolve("x.txt"); Files.writeString(text, "abc");
        Path corrupt = directory.resolve("x.wav"); Files.writeString(corrupt, "not a wave");
        assertThrows(IOException.class, () -> AudioFiles.read(text, null));
        assertThrows(IOException.class, () -> AudioFiles.read(corrupt, null));
        assertThrows(IOException.class, () -> AudioFiles.read(directory.resolve("missing.wav"), null));
    }
    @Test void mp3WithoutVoiceChatHasUsefulError() throws IOException {
        Path file = directory.resolve("x.mp3"); Files.write(file, new byte[]{1});
        IOException failure = assertThrows(IOException.class, () -> AudioFiles.read(file, null));
        assertTrue(failure.getMessage().contains("Simple Voice Chat"));
    }
    @Test void mp3DecoderResultsAreConvertedToMono48k() throws IOException {
        Path file = directory.resolve("x.mp3"); Files.write(file, new byte[]{1});
        short[] samples = AudioFiles.read(file, stream -> new AudioFiles.Decoded(new short[]{1000, 3000, 1000, 3000}, 2, 24000));
        assertArrayEquals(new short[]{2000, 2000, 2000, 2000}, samples);
    }
}
