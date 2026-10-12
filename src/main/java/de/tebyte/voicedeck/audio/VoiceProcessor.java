package de.tebyte.voicedeck.audio;

import de.tebyte.voicedeck.config.DeckConfig;
import java.util.Arrays;

/** Stateful 48 kHz mono processor; accessed only from the voice chat audio thread. */
public final class VoiceProcessor {
    private final double[] pitchBuffer = new double[8192];
    private final double[] echoBuffer = new double[8640];
    private int write, echoWrite;
    private double phase, oscillator, previousInput, highpass, lowpass;
    private DeckConfig.Effect previousEffect;
    private double previousPitch = Double.NaN;
    private long lastFrame;

    public void reset() {
        Arrays.fill(pitchBuffer, 0);
        Arrays.fill(echoBuffer, 0);
        write = echoWrite = 0;
        phase = oscillator = previousInput = highpass = lowpass = 0;
    }

    public short[] process(short[] input, DeckConfig config) {
        if (!config.enabled) {
            previousEffect = null;
            return input;
        }
        DeckConfig.Effect effect = config.effect;
        double semitones = switch (effect) {
            case DEEP -> -5;
            case HIGH -> 7;
            case CUSTOM -> config.pitch;
            default -> 0;
        };
        long now = System.nanoTime();
        if (effect != previousEffect || semitones != previousPitch || now - lastFrame > 250_000_000L) reset();
        previousEffect = effect;
        previousPitch = semitones;
        lastFrame = now;
        double gain = config.voiceGain, mix = config.mix;
        short[] output = new short[input.length];
        double ratio = Math.pow(2, semitones / 12);
        for (int i = 0; i < input.length; i++) {
            double dry = input[i] / 32768.0, wet = dry;
            if (semitones != 0) wet = shift(dry, ratio);
            switch (effect) {
                case ROBOT -> {
                    wet = Math.tanh(dry * 2) * Math.sin(oscillator) * 0.85;
                    oscillator = (oscillator + 2 * Math.PI * 70 / Pcm.SAMPLE_RATE) % (2 * Math.PI);
                }
                case RADIO -> {
                    highpass = 0.962 * (highpass + dry - previousInput);
                    previousInput = dry;
                    lowpass += 0.31 * (highpass - lowpass);
                    wet = Math.round(Math.tanh(lowpass * 2.5) * 96) / 96.0 * 0.75;
                }
                case ECHO -> {
                    double delayed = echoBuffer[echoWrite];
                    echoBuffer[echoWrite] = dry + delayed * 0.32;
                    echoWrite = (echoWrite + 1) % echoBuffer.length;
                    wet = dry * 0.78 + delayed * 0.42;
                }
                default -> {}
            }
            output[i] = Pcm.clamp((dry * (1 - mix) + wet * mix) * gain * 32768);
        }
        return output;
    }

    /** Two cross-faded read heads preserve frame length and continuity between packets. */
    private double shift(double input, double ratio) {
        pitchBuffer[write] = input;
        double second = (phase + 0.5) % 1;
        double weight = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        double output = read(64 + phase * 2880) * weight + read(64 + second * 2880) * (1 - weight);
        write = (write + 1) % pitchBuffer.length;
        phase += (1 - ratio) / 2880;
        phase -= Math.floor(phase);
        return output;
    }

    private double read(double delay) {
        double position = (write - delay + pitchBuffer.length) % pitchBuffer.length;
        int left = (int) position;
        return pitchBuffer[left] + (pitchBuffer[(left + 1) % pitchBuffer.length] - pitchBuffer[left]) * (position - left);
    }
}
