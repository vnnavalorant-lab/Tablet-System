package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Tocador de musicas do app Musica: toca as duas faixas proprias do mod (Campfire Tales e Echoes Below).
 * A musica continua tocando em segundo plano se o tablet for fechado; o botao Parar (ou o quadrado) para.
 */
public final class MusicPlayer {
    public record Track(String name, String artist, String sound) { }

    /** Faixas (arquivos em assets/tablet_inception/sounds/<sound>.ogg, declarados em sounds.json). */
    public static final Track[] TRACKS = {
            new Track("Campfire Tales", "Scute", "campfire_tales"),
            new Track("Echoes Below", "Scute", "echoes_below"),
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
        SoundEvent ev = SoundEvent.of(new Identifier(TabletInceptionMod.MOD_ID, TRACKS[index].sound()));
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
