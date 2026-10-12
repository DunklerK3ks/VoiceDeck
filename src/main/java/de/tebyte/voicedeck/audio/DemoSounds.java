package de.tebyte.voicedeck.audio;

public final class DemoSounds {
    public static final String[] NAMES = {"Level Up", "Alarm", "Melodie", "Boop"};
    private DemoSounds() {}
    public static short[] create(int type) {
        int length = switch (type) { case 0 -> 33600; case 1 -> 48000; case 2 -> 57600; default -> 12000; };
        short[] samples = new short[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) Pcm.SAMPLE_RATE;
            double frequency = switch (type) {
                case 0 -> new double[]{523.25, 659.25, 783.99, 1046.5}[Math.min(3, (int) (t / 0.16))];
                case 1 -> 640 + 240 * Math.sin(2 * Math.PI * 3 * t);
                case 2 -> new double[]{523.25, 659.25, 783.99, 659.25, 523.25, 1046.5}[Math.min(5, (int) (t / 0.2))];
                default -> 600 - t * 1100;
            };
            phase += 2 * Math.PI * frequency / Pcm.SAMPLE_RATE;
            double envelope = Math.min(1, i / 240.0) * Math.min(1, (length - i) / 1200.0);
            samples[i] = Pcm.clamp(Math.sin(phase) * envelope * 10000);
        }
        return samples;
    }
}
