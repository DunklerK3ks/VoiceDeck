package de.tebyte.voicedeck.audio;

import de.tebyte.voicedeck.config.DeckConfig;
import java.util.Arrays;
import java.util.function.Consumer;

public final class AudioEngine implements AutoCloseable {
    public enum Connection { MISSING, DISCONNECTED, DISABLED, MUTED, CONNECTED }
    private final DeckConfig config;
    private final Consumer<String> messages;
    private final LocalPlayer local = new LocalPlayer();
    private final VoiceProcessor processor = new VoiceProcessor();
    private volatile Connection connection = Connection.MISSING;
    private volatile String playingName = "";
    private short[] outgoing;
    private int cursor;
    private long startedAt, lastPoll;
    private volatile boolean recording;
    private short[] testSamples;
    private int recorded;
    private volatile long recordUntil;
    public volatile double level;

    public AudioEngine(DeckConfig config, Consumer<String> messages) {
        this.config = config;
        this.messages = messages;
    }

    public synchronized void connection(Connection value) {
        connection = value;
        if (value == Connection.DISCONNECTED || value == Connection.DISABLED || value == Connection.MISSING
                || (value == Connection.MUTED && !config.sendWhenMuted)) outgoing = null;
    }
    public Connection connection() { return connection; }
    public boolean canSend() { return connection == Connection.CONNECTED || (connection == Connection.MUTED && config.sendWhenMuted); }

    public synchronized void play(String name, short[] samples, boolean preview) {
        stop();
        if (samples.length == 0) return;
        playingName = name;
        boolean useLocal = preview || config.output != DeckConfig.Output.VOICECHAT;
        boolean useNetwork = !preview && config.output != DeckConfig.Output.LOCAL;
        if (useNetwork && !canSend()) {
            messages.accept(connection == Connection.MUTED ? "Mikrofon stumm: Sound wird nicht gesendet" : "Voice Chat nicht verbunden: keine Übertragung");
        } else if (useNetwork) {
            outgoing = samples;
            cursor = 0;
            startedAt = lastPoll = System.nanoTime();
        }
        if (useLocal) local.play(samples, config.boardGain, messages);
    }

    /** Exactly one frame per MergeClientSoundEvent; no timer drives network audio. */
    public synchronized short[] nextNetworkFrame() {
        if (outgoing == null || !canSend() || recording) return null;
        short[] frame = new short[Pcm.FRAME_SIZE];
        int count = Math.min(frame.length, outgoing.length - cursor);
        for (int i = 0; i < count; i++) frame[i] = Pcm.clamp(outgoing[cursor + i] * config.boardGain);
        cursor += count;
        lastPoll = System.nanoTime();
        if (cursor >= outgoing.length) outgoing = null;
        return frame;
    }

    public short[] process(short[] input) {
        short[] result = processor.process(input, config);
        double sum = 0;
        for (short sample : result) sum += (double) sample * sample;
        level = result.length == 0 ? 0 : Math.sqrt(sum / result.length) / 32768;
        return result;
    }

    public synchronized boolean captureTest(short[] processed) {
        if (!recording) return false;
        int count = Math.min(processed.length, testSamples.length - recorded);
        System.arraycopy(processed, 0, testSamples, recorded, count);
        recorded += count;
        return true;
    }

    public synchronized void startTest() {
        stop();
        if (connection == Connection.MISSING || connection == Connection.DISCONNECTED || connection == Connection.DISABLED) {
            messages.accept("Mikrofontest benötigt eine aktive Voice-Chat-Verbindung");
            return;
        }
        testSamples = new short[Pcm.SAMPLE_RATE * 5];
        recorded = 0;
        recordUntil = System.nanoTime() + 5_000_000_000L;
        recording = true;
        messages.accept("5 Sekunden sprechen / Sprechtaste halten. Test wird nicht gesendet.");
    }
    public boolean isRecording() { return recording; }
    public int recordSecondsLeft() { return (int) Math.max(0, Math.ceil((recordUntil - System.nanoTime()) / 1e9)); }

    public synchronized void tick() {
        if (recording && System.nanoTime() >= recordUntil) {
            recording = false;
            short[] recordingData = Arrays.copyOf(testSamples, recorded);
            testSamples = null;
            if (recorded == 0) messages.accept("Keine Sprache aufgenommen. Mikrofon aktivieren / Sprechtaste halten.");
            else { playingName = "Mikrofontest"; local.play(recordingData, 0.8, messages); }
        }
        if (outgoing != null && System.nanoTime() - Math.max(startedAt, lastPoll) > 1_500_000_000L) {
            outgoing = null;
            messages.accept("Keine Audiodaten von Voice Chat. Mikrofon und Verbindung prüfen.");
        }
        level *= 0.92;
        if (outgoing == null && !local.isPlaying() && !recording) playingName = "";
    }

    public synchronized void stop() {
        outgoing = null;
        recording = false;
        testSamples = null;
        playingName = "";
        local.stop();
    }
    public String playingName() { return playingName; }
    @Override public void close() { stop(); local.close(); }
}
