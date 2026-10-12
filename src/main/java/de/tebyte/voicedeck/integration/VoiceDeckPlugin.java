package de.tebyte.voicedeck.integration;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;
import de.tebyte.voicedeck.VoiceDeckClient;
import de.tebyte.voicedeck.audio.AudioEngine;
import de.tebyte.voicedeck.audio.AudioFiles;
import java.io.IOException;

/** Loaded exclusively by Simple Voice Chat. The base mod does not link to its API. */
public final class VoiceDeckPlugin implements VoicechatPlugin {
    @Override public String getPluginId() { return VoiceDeckClient.MOD_ID; }

    @Override public void initialize(VoicechatApi api) {
        VoiceDeckClient.whenReady(deck -> {
            deck.library().mp3Reader(stream -> {
                Mp3Decoder decoder = api.createMp3Decoder(stream);
                if (decoder == null) throw new IOException("MP3-Decoder nicht verfügbar");
                short[] samples = decoder.decode();
                var format = decoder.getAudioFormat();
                return new AudioFiles.Decoded(samples, format.getChannels(), format.getSampleRate());
            });
            deck.engine().connection(AudioEngine.Connection.DISCONNECTED);
        });
    }

    @Override public void registerEvents(EventRegistration events) {
        events.registerEvent(ClientVoicechatInitializationEvent.class, this::initializeClient);
        events.registerEvent(ClientVoicechatConnectionEvent.class, event -> {
            VoiceDeckClient deck = VoiceDeckClient.instance();
            if (deck != null) {
                deck.engine().stop();
                deck.engine().connection(event.isConnected() ? state(event.getVoicechat()) : AudioEngine.Connection.DISCONNECTED);
            }
        });
        events.registerEvent(MicrophoneMuteEvent.class, event -> updateState(event.getVoicechat()));
        events.registerEvent(VoicechatDisableEvent.class, event -> updateState(event.getVoicechat()));
        events.registerEvent(MergeClientSoundEvent.class, event -> {
            VoiceDeckClient deck = VoiceDeckClient.instance();
            if (deck == null) return;
            updateState(event.getVoicechat());
            short[] frame = deck.engine().nextNetworkFrame();
            if (frame != null) event.mergeAudio(frame);
        });
        events.registerEvent(ClientSoundEvent.class, event -> {
            VoiceDeckClient deck = VoiceDeckClient.instance();
            if (deck == null) return;
            short[] processed = deck.engine().process(event.getRawAudio());
            if (deck.engine().captureTest(processed)) event.cancel();
            else event.setRawAudio(processed);
        });
    }

    private void initializeClient(ClientVoicechatInitializationEvent event) {
        VoicechatClientApi api = event.getVoicechat();
        VoiceDeckClient.whenReady(deck -> updateState(api));
    }

    private void updateState(VoicechatClientApi api) {
        VoiceDeckClient deck = VoiceDeckClient.instance();
        if (deck != null) deck.engine().connection(state(api));
    }
    private AudioEngine.Connection state(VoicechatClientApi api) {
        if (api.isDisconnected()) return AudioEngine.Connection.DISCONNECTED;
        if (api.isDisabled()) return AudioEngine.Connection.DISABLED;
        if (api.isMuted()) return AudioEngine.Connection.MUTED;
        return AudioEngine.Connection.CONNECTED;
    }
}
