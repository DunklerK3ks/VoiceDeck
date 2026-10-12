package de.tebyte.voicedeck.audio;

public final class Pcm {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SIZE = 960;
    public static final int MAX_SECONDS = 120;
    private Pcm() {}

    public static short clamp(double value) {
        if (!Double.isFinite(value)) return 0;
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value)));
    }

    public static short[] resample(short[] interleaved, int channels, float sampleRate) {
        if (channels < 1 || channels > 32 || !Float.isFinite(sampleRate) || sampleRate < 1000 || sampleRate > 384000)
            throw new IllegalArgumentException("Ungültiges Audioformat");
        int frames = interleaved.length / channels;
        if (frames == 0) throw new IllegalArgumentException("Die Audiodatei ist leer");
        long count = Math.round(frames * (double) SAMPLE_RATE / sampleRate);
        if (count > (long) SAMPLE_RATE * MAX_SECONDS)
            throw new IllegalArgumentException("Maximal " + MAX_SECONDS + " Sekunden pro Sound");
        short[] result = new short[(int) Math.max(1, count)];
        double ratio = sampleRate / SAMPLE_RATE;
        for (int i = 0; i < result.length; i++) {
            double position = Math.min(frames - 1, i * ratio);
            int left = (int) position, right = Math.min(frames - 1, left + 1);
            double a = 0, b = 0;
            for (int c = 0; c < channels; c++) {
                a += interleaved[left * channels + c];
                b += interleaved[right * channels + c];
            }
            result[i] = clamp((a + (b - a) * (position - left)) / channels);
        }
        return result;
    }

    public static byte[] bytes(short[] samples) {
        byte[] result = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            result[i * 2] = (byte) samples[i];
            result[i * 2 + 1] = (byte) (samples[i] >> 8);
        }
        return result;
    }

    public static short[] gain(short[] input, double gain) {
        short[] output = new short[input.length];
        for (int i = 0; i < input.length; i++) output[i] = clamp(input[i] * gain);
        return output;
    }
}
