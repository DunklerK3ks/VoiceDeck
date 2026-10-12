package de.tebyte.voicedeck.audio;

import de.tebyte.voicedeck.config.DeckConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class VoiceProcessorTest {
    private DeckConfig config(DeckConfig.Effect effect) {
        DeckConfig config = new DeckConfig(); config.enabled = true; config.effect = effect; return config;
    }
    @Test void bypassIsBitExactWhenDisabledOrDry() {
        short[] input = {-32768, -100, 0, 32767};
        DeckConfig config = new DeckConfig();
        assertSame(input, new VoiceProcessor().process(input, config));
        config.enabled = true; config.effect = DeckConfig.Effect.ROBOT; config.mix = 0;
        assertArrayEquals(input, new VoiceProcessor().process(input, config));
    }
    @ParameterizedTest @EnumSource(DeckConfig.Effect.class)
    void allEffectsPreserveFrameSizeAndDoNotCreateNoiseFromSilence(DeckConfig.Effect effect) {
        VoiceProcessor processor = new VoiceProcessor();
        for (int frame = 0; frame < 30; frame++) {
            short[] result = processor.process(new short[Pcm.FRAME_SIZE], config(effect));
            assertArrayEquals(new short[Pcm.FRAME_SIZE], result);
        }
    }
    @Test void voiceGainClipsAtPcmLimits() {
        DeckConfig config = config(DeckConfig.Effect.NATURAL); config.voiceGain = 2;
        assertArrayEquals(new short[]{32767, -32768}, new VoiceProcessor().process(new short[]{30000, -30000}, config));
    }
    @Test void echoPersistsAcrossPacketBoundariesAndResetsOnEffectChange() {
        DeckConfig config = config(DeckConfig.Effect.ECHO); VoiceProcessor processor = new VoiceProcessor();
        short[] output = new short[Pcm.FRAME_SIZE * 20];
        for (int frame = 0; frame < 20; frame++) {
            short[] input = new short[Pcm.FRAME_SIZE]; if (frame == 0) input[0] = 20000;
            System.arraycopy(processor.process(input, config), 0, output, frame * Pcm.FRAME_SIZE, Pcm.FRAME_SIZE);
        }
        assertEquals(15600, output[0]); assertEquals(8400, output[8640]); assertEquals(2688, output[17280]);
        config.effect = DeckConfig.Effect.NATURAL; processor.process(new short[960], config);
        config.effect = DeckConfig.Effect.ECHO;
        assertArrayEquals(new short[960], processor.process(new short[960], config));
    }
    @Test void deepAndHighPresetsActuallyShiftFrequencyWithoutChangingDuration() {
        assertEquals(440 * Math.pow(2, -5 / 12.0), pitchFrequency(DeckConfig.Effect.DEEP), 12);
        assertEquals(440 * Math.pow(2, 7 / 12.0), pitchFrequency(DeckConfig.Effect.HIGH), 12);
    }
    @Test void customPitchUsesItsConfiguredSemitones() {
        assertEquals(440 * Math.pow(2, -12 / 12.0), pitchFrequency(DeckConfig.Effect.CUSTOM), 12);
    }
    private double pitchFrequency(DeckConfig.Effect effect) {
        DeckConfig config = config(effect); config.pitch = -12;
        VoiceProcessor processor = new VoiceProcessor(); short[] output = new short[96000];
        for (int offset = 0; offset < output.length; offset += 960) {
            short[] input = new short[960];
            for (int i = 0; i < input.length; i++) input[i] = Pcm.clamp(Math.sin(2 * Math.PI * 440 * (offset + i) / Pcm.SAMPLE_RATE) * 12000);
            System.arraycopy(processor.process(input, config), 0, output, offset, 960);
        }
        short[] stable = Arrays.copyOfRange(output, 48000, 96000);
        double strongest = 0, peakFrequency = 0;
        for (int frequency = 150; frequency < 850; frequency += 2) {
            double coefficient = 2 * Math.cos(2 * Math.PI * frequency / Pcm.SAMPLE_RATE);
            double a = 0, b = 0;
            for (short sample : stable) { double next = sample + coefficient * a - b; b = a; a = next; }
            double power = a * a + b * b - coefficient * a * b;
            if (power > strongest) { strongest = power; peakFrequency = frequency; }
        }
        return peakFrequency;
    }
}
