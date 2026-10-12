package de.tebyte.voicedeck.audio;

import de.tebyte.voicedeck.config.DeckConfig;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioEngineTest {
    private DeckConfig config() { DeckConfig config = new DeckConfig(); config.output = DeckConfig.Output.VOICECHAT; config.boardGain = 1; return config; }
    @Test void streamsFrameSizedPacketsAndPadsOnlyTheLastPacket() {
        try (AudioEngine engine = new AudioEngine(config(), ignored -> {})) {
            engine.connection(AudioEngine.Connection.CONNECTED);
            short[] clip = new short[1000]; Arrays.fill(clip, (short) 1234);
            engine.play("test", clip, false);
            short[] first = engine.nextNetworkFrame(), second = engine.nextNetworkFrame();
            assertEquals(960, first.length); assertEquals(960, second.length);
            for (short sample : first) assertEquals(1234, sample);
            for (int i = 0; i < second.length; i++) assertEquals(i < 40 ? 1234 : 0, second[i]);
            assertNull(engine.nextNetworkFrame());
        }
    }
    @Test void mutedPlaybackRequiresExplicitOptInAndNeverResumesAStoppedClip() {
        DeckConfig config = config();
        try (AudioEngine engine = new AudioEngine(config, ignored -> {})) {
            engine.connection(AudioEngine.Connection.MUTED); engine.play("test", new short[3000], false);
            assertNull(engine.nextNetworkFrame());
            config.sendWhenMuted = true; engine.play("test", new short[3000], false);
            assertNotNull(engine.nextNetworkFrame());
            config.sendWhenMuted = false; engine.connection(AudioEngine.Connection.MUTED);
            engine.connection(AudioEngine.Connection.CONNECTED); assertNull(engine.nextNetworkFrame());
        }
    }
    @Test void disconnectDisableAndStopClearPendingAudio() {
        for (AudioEngine.Connection state : new AudioEngine.Connection[]{AudioEngine.Connection.DISCONNECTED, AudioEngine.Connection.DISABLED, AudioEngine.Connection.MISSING}) {
            try (AudioEngine engine = new AudioEngine(config(), ignored -> {})) {
                engine.connection(AudioEngine.Connection.CONNECTED); engine.play("test", new short[3000], false);
                engine.connection(state); assertNull(engine.nextNetworkFrame());
                engine.connection(AudioEngine.Connection.CONNECTED); assertNull(engine.nextNetworkFrame());
                engine.play("test", new short[3000], false); engine.stop(); assertNull(engine.nextNetworkFrame());
            }
        }
    }
    @Test void microphoneTestConsumesFramesAndSuppressesSoundboardUntilStopped() {
        try (AudioEngine engine = new AudioEngine(config(), ignored -> {})) {
            engine.connection(AudioEngine.Connection.CONNECTED);
            engine.play("test", new short[3000], false); engine.startTest();
            assertTrue(engine.isRecording()); assertNull(engine.nextNetworkFrame());
            assertTrue(engine.captureTest(new short[960]));
            engine.stop(); assertFalse(engine.isRecording()); assertFalse(engine.captureTest(new short[960]));
        }
    }
}
