package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bloco de notas do tablet. O texto fica salvo em config/tablet_inception/notes.txt
 * e e salvo automaticamente pouco depois de cada alteracao.
 */
public final class Notes {
    public static final int MAX = 4000;

    private static String text = "";
    private static boolean loaded;
    private static boolean dirty;
    private static long lastEdit;

    private Notes() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tablet_inception").resolve("notes.txt");
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            String s = Files.readString(f, StandardCharsets.UTF_8).replace("\r", "");
            text = s.length() > MAX ? s.substring(0, MAX) : s;
        } catch (IOException | RuntimeException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel ler as notas do tablet: {}", e.toString());
        }
    }

    public static String text() {
        return text;
    }

    /** Acrescenta texto ao final (respeitando o limite). */
    public static void append(String s) {
        if (s == null || s.isEmpty() || text.length() >= MAX) return;
        int room = MAX - text.length();
        text += s.length() > room ? s.substring(0, room) : s;
        touch();
    }

    public static void backspace() {
        if (text.isEmpty()) return;
        int n = text.length();
        // nao corta um par substituto (emoji etc.) ao meio
        int cut = n >= 2 && Character.isLowSurrogate(text.charAt(n - 1)) && Character.isHighSurrogate(text.charAt(n - 2)) ? 2 : 1;
        text = text.substring(0, n - cut);
        touch();
    }

    private static void touch() {
        dirty = true;
        lastEdit = System.currentTimeMillis();
    }

    /** Chamado a cada frame: salva se houve alteracao ha mais de 1,5 s. */
    public static void tick() {
        if (dirty && System.currentTimeMillis() - lastEdit > 1500) save();
    }

    public static void save() {
        if (!dirty) return;
        dirty = false;
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel salvar as notas do tablet: {}", e.toString());
        }
    }
}
