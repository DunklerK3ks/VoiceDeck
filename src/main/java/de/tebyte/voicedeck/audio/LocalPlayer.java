package de.tebyte.voicedeck.audio;

import javax.sound.sampled.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public final class LocalPlayer implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VoiceDeck-Playback");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<SourceDataLine> current = new AtomicReference<>();
    private volatile boolean playing;

    public void play(short[] samples, double gain, Consumer<String> error) {
        stop();
        long ticket = generation.get();
        playing = true;
        worker.execute(() -> {
            SourceDataLine line = null;
            try {
                if (generation.get() != ticket) return;
                AudioFormat format = new AudioFormat(Pcm.SAMPLE_RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, Pcm.FRAME_SIZE * 8);
                if (generation.get() != ticket) return;
                current.set(line);
                line.start();
                for (int offset = 0; offset < samples.length && generation.get() == ticket; offset += Pcm.FRAME_SIZE) {
                    int length = Math.min(Pcm.FRAME_SIZE, samples.length - offset);
                    short[] frame = new short[length];
                    for (int i = 0; i < length; i++) frame[i] = Pcm.clamp(samples[offset + i] * gain);
                    byte[] bytes = Pcm.bytes(frame);
                    int written = 0;
                    while (written < bytes.length && generation.get() == ticket) {
                        int count = line.write(bytes, written, bytes.length - written);
                        if (count <= 0) break;
                        written += count;
                    }
                }
                if (generation.get() == ticket) line.drain();
            } catch (Exception ex) {
                if (generation.get() == ticket) error.accept("Audioausgabe nicht verfügbar: " + ex.getMessage());
            } finally {
                if (line != null) {
                    current.compareAndSet(line, null);
                    line.close();
                }
                if (generation.get() == ticket) playing = false;
            }
        });
    }

    public void stop() {
        generation.incrementAndGet();
        playing = false;
        SourceDataLine line = current.getAndSet(null);
        if (line != null) { line.stop(); line.flush(); line.close(); }
    }
    public boolean isPlaying() { return playing; }
    @Override public void close() { stop(); worker.shutdownNow(); }
}
