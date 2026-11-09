package com.example.tabletinception.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Tocador de musicas do app Musica: usa as faixas dos discos de musica do Minecraft.
 * A musica continua tocando em segundo plano se o tablet for fechado; o botao Parar (ou o quadrado) para.
 */
public final class MusicPlayer {
    public record Track(String name, String artist, String sound) { }

    /** Faixas (id do som = minecraft:music_disc.<sound>). */
    public static final Track[] TRACKS = {
            new Track("13", "C418", "13"),
            new Track("Cat", "C418", "cat"),
            new Track("Blocks", "C418", "blocks"),
            new Track("Chirp", "C418", "chirp"),
            new Track("Far", "C418", "far"),
            new Track("Mall", "C418", "mall"),
            new Track("Mellohi", "C418", "mellohi"),
            new Track("Stal", "C418", "stal"),
            new Track("Strad", "C418", "strad"),
            new Track("Ward", "C418", "ward"),
            new Track("11", "C418", "11"),
            new Track("Wait", "C418", "wait"),
            new Track("Otherside", "Lena Raine", "otherside"),
            new Track("5", "Samuel \u00c5berg", "5"),
            new Track("Pigstep", "Lena Raine", "pigstep"),
            new Track("Relic", "Aaron Cherof", "relic"),
    };

    private static int current = -1;
    private static SoundInstance instance;
    private static long startMs;

    private MusicPlayer() {
    }

    public static int current() {
        return current;
    }

    public static void play(MinecraftClient mc, int index) {
        if (index < 0 || index >= TRACKS.length) return;
        SoundEvent ev = Registries.SOUND_EVENT.get(new Identifier("minecraft", "music_disc." + TRACKS[index].sound()));
        if (ev == null) return;
        stop(mc);
        mc.getSoundManager().stopSounds(null, SoundCategory.MUSIC);   // silencia a musica ambiente do jogo
        instance = PositionedSoundInstance.music(ev);
        mc.getSoundManager().play(instance);
        current = index;
        startMs = System.currentTimeMillis();
    }

    public static void stop(MinecraftClient mc) {
        if (instance != null) {
            mc.getSoundManager().stop(instance);
        }
        instance = null;
        current = -1;
    }

    public static boolean isPlaying(MinecraftClient mc) {
        if (instance == null) return false;
        // o motor de som pode demorar um instante para registrar a faixa
        return System.currentTimeMillis() - startMs < 1500 || mc.getSoundManager().isPlaying(instance);
    }

    public static long elapsedSeconds() {
        return Math.max(0, (System.currentTimeMillis() - startMs) / 1000);
    }
}
