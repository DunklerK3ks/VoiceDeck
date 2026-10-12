package de.tebyte.voicedeck.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcmTest {
    @Test void clipsInsteadOfWrappingOrLeakingNonFiniteValues() {
        assertEquals(32767, Pcm.clamp(80000));
        assertEquals(-32768, Pcm.clamp(-80000));
        assertEquals(0, Pcm.clamp(Double.NaN));
        assertEquals(0, Pcm.clamp(Double.POSITIVE_INFINITY));
    }
    @Test void downmixesStereoAndResamplesToVoiceChatRate() {
        short[] stereo = new short[24000 * 2];
        for (int i = 0; i < stereo.length; i += 2) { stereo[i] = 10000; stereo[i + 1] = -6000; }
        short[] mono = Pcm.resample(stereo, 2, 24000);
        assertEquals(48000, mono.length);
        for (short sample : mono) assertEquals(2000, sample);
    }
    @Test void rejectsEmptyInvalidAndOverlongAudio() {
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[0], 1, 48000));
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[1], 0, 48000));
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[1], 1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Pcm.resample(new short[121000], 1, 1000));
    }
    @Test void producesSignedLittleEndianPcm() {
        assertArrayEquals(new byte[]{0, -128, -1, 127, -1, -1}, Pcm.bytes(new short[]{-32768, 32767, -1}));
    }
}
