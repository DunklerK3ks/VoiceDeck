package de.tebyte.voicedeck.smoke;

import de.tebyte.voicedeck.VoiceDeckClient;
import de.tebyte.voicedeck.audio.AudioEngine;
import de.tebyte.voicedeck.config.DeckConfig;
import de.tebyte.voicedeck.ui.VoiceDeckScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;

public final class MenuSmokeTest implements ClientModInitializer {
    private int stage, ticks;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (stage == 0) {
                if (client.gui.screen() instanceof TitleScreen && client.gui.overlay() == null && !VoiceDeckClient.instance().library().busy()
                        && VoiceDeckClient.instance().library().sounds().size() >= 4) {
                    AudioEngine.Connection connection = VoiceDeckClient.instance().engine().connection();
                    boolean without = Boolean.getBoolean("voicedeck.smoke.withoutVoiceChat");
                    if ((connection == AudioEngine.Connection.MISSING) != without) throw new AssertionError("Unexpected Voice Chat state: " + connection);
                    if (VoiceDeckClient.instance().library().supportsMp3() == without) throw new AssertionError("Unexpected MP3 availability");
                    VoiceDeckClient.LOGGER.info("SMOKE: initialized; voicechat={}", connection);
                    client.gui.setScreen(new VoiceDeckScreen(client.gui.screen(), VoiceDeckClient.instance())); stage = 1; ticks = 0;
                }
                return;
            }
            if (++ticks < 20) return;
            ticks = 0;
            switch (stage++) {
                case 1 -> { screenshot(client, "soundboard"); press(client, "tab.voice"); }
                case 2 -> {
                    screenshot(client, "voice-changer");
                    press(client, "tab.soundboard"); press(client, "import"); press(client, "sound_folder");
                    // "." addresses the displayed directory without exposing a local machine path in published screenshots.
                    client.gui.screen().children().stream().filter(child -> child instanceof EditBox)
                            .map(child -> (EditBox) child).findFirst().orElseThrow().setValue(".");
                }
                case 3 -> { screenshot(client, "file-browser"); press(client, "back"); }
                case 4 -> {
                    if (!(client.gui.screen() instanceof VoiceDeckScreen)) throw new AssertionError("Return to VoiceDeck failed");
                    if (!Boolean.getBoolean("voicedeck.smoke.withoutVoiceChat")) verifyVoiceEvents();
                    VoiceDeckClient.LOGGER.info("SMOKE PASS: soundboard, voice changer, in-game file browser, return navigation");
                    client.stop();
                }
            }
        });
    }
    /** Synthetic PCM goes through Simple Voice Chat's real registered event dispatcher. */
    private void verifyVoiceEvents() {
        VoiceDeckClient deck = VoiceDeckClient.instance();
        DeckConfig config = deck.config();
        boolean previousEnabled = config.enabled;
        DeckConfig.Effect previousEffect = config.effect;
        double previousGain = config.voiceGain, previousMix = config.mix;
        AudioEngine.Connection previousConnection = deck.engine().connection();
        try {
            Class<?> managerClass = Class.forName("de.maxhenkel.voicechat.plugins.ClientPluginManager");
            Object manager = managerClass.getMethod("instance").invoke(null);
            var onSound = managerClass.getMethod("onClientSound", short[].class, boolean.class);
            config.enabled = true; config.effect = DeckConfig.Effect.NATURAL; config.voiceGain = 0.5; config.mix = 1;
            short[] input = new short[960]; java.util.Arrays.fill(input, (short) 10000);
            short[] result = (short[]) onSound.invoke(manager, input, false);
            if (result == null || result.length != 960 || result[0] != 5000) throw new AssertionError("Voice effect did not process the outgoing event");
            deck.engine().connection(AudioEngine.Connection.CONNECTED);
            deck.engine().startTest();
            if (onSound.invoke(manager, input, false) != null) throw new AssertionError("Microphone test failed to cancel the outgoing event");
            VoiceDeckClient.LOGGER.info("SMOKE PASS: actual Simple Voice Chat audio processing and microphone-test packet cancellation");
        } catch (ReflectiveOperationException ex) { throw new AssertionError("Voice Chat event test failed", ex); }
        finally {
            deck.engine().stop(); deck.engine().connection(previousConnection);
            config.enabled = previousEnabled; config.effect = previousEffect; config.voiceGain = previousGain; config.mix = previousMix;
        }
    }
    private void press(Minecraft client, String key) {
        String label = Component.translatable("voicedeck." + key).getString();
        client.gui.screen().children().stream().filter(child -> child instanceof Button).map(child -> (Button) child)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing button " + key)).onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEY_RETURN, 0));
    }
    private void screenshot(Minecraft client, String name) {
        String prefix = Boolean.getBoolean("voicedeck.smoke.withoutVoiceChat") ? "local-" : "voicechat-";
        Screenshot.grab(client.gameDirectory, "voicedeck-26.3-" + prefix + name + ".png", client.gameRenderer.mainRenderTarget(), 1,
                message -> VoiceDeckClient.LOGGER.info("SMOKE screenshot: {}", message.getString()));
    }
}
