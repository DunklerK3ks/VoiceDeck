# SoundDeck for Simple Voice Chat

Bring your own sounds and voice effects into Minecraft. SoundDeck adds an **in-game soundboard and real-time voice changer**, with controls, sound import, and settings directly inside the game.

**Minecraft Java 26.3 · Fabric · Client-side**

The current 1.1.0 build uses the name **VoiceDeck** in its menus, configuration folder, and JAR filename. This Modrinth project distributes that same build.

## Features

- **Eight soundboard slots** with configurable hotkeys.
- Import **WAV and MP3** files through an in-game file browser or drag and drop.
- Play sounds locally, through Simple Voice Chat, or both.
- Voice presets: **Deep, High, Robot, Radio, and Echo**.
- Custom pitch from **−12 to +12 semitones**, effect mix, and volume controls.
- A **five-second microphone test** that records and plays back locally.
- Saved settings and slot assignments, with German and English menus.
- Four original example sounds: Level Up, Alarm, Melody, and Boop.

![In-game soundboard](https://raw.githubusercontent.com/DunklerK3ks/VoiceDeck/main/docs/screenshots/soundboard.png)

## Installation

1. Install **Fabric Loader 0.19.5 or newer** for Minecraft Java **26.3**.
2. Put the matching **Fabric API** and this mod's JAR into your profile's `mods` folder.
3. Install **Simple Voice Chat 2.6.24 or newer for Fabric 26.3** for voice effects, MP3 import, and sound transmission.
4. Start Minecraft and press **B** to open the menu.

The local WAV soundboard works without Simple Voice Chat. Multiplayer voice effects and sound transmission need a working Simple Voice Chat connection and a server configured for it. Other players need Simple Voice Chat; they do not need SoundDeck.

## Controls

| Default key | Action |
| --- | --- |
| B | Open the menu |
| R | Toggle the voice changer |
| Num 1–8 | Play a soundboard slot |
| Num 0 | Stop playback and microphone test |

All keys can be changed in Minecraft's controls settings. Enable Num Lock when using the number pad.

![Voice effects menu](https://raw.githubusercontent.com/DunklerK3ks/VoiceDeck/main/docs/screenshots/voice-changer.png)

## Your sound library

Import your own audio files from inside Minecraft. Imported sounds and settings are stored in `config/voicedeck/`. Sounds are limited to **120 seconds and 24 MB per file**.

The voice changer processes outgoing voice-chat audio, including transmitted soundboard audio. Local sound previews play the original file. Broadcasting while your microphone is muted is **off by default** and can be enabled deliberately in the menu.

![In-game file browser](https://raw.githubusercontent.com/DunklerK3ks/VoiceDeck/main/docs/screenshots/file-browser.png)

## Requirements and development status

This build supports **Minecraft 26.3 with Fabric and Java 25**. The Simple Voice Chat 2.6.24 release used for 26.3 is marked as beta by its author.

The build passes **27 automated tests** and was checked in real Minecraft development clients. A complete two-player server test is still pending.

[Source code and build instructions](https://github.com/DunklerK3ks/VoiceDeck) · [Report a bug](https://github.com/DunklerK3ks/VoiceDeck/issues)

The mod is distributed under the **MIT license**. The project icon was created with AI image generation.

