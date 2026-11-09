package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Cores da interface do tablet (painel superior e painel lateral).
 * 0 = cor padrao. Salvo em config/tablet_inception/theme.properties.
 */
public final class Theme {
    /** Paleta (RGB). O indice 0 e "padrao". */
    public static final int[] PALETTE = {
            0,
            0xE53935, // vermelho
            0xFB8C00, // laranja
            0xFDD835, // amarelo
            0x43A047, // verde
            0x00ACC1, // ciano
            0x1E88E5, // azul
            0x5E35B1, // roxo
            0xD81B60, // rosa
            0x212121, // preto
            0xFFFFFF  // branco (as barras ficam translucidas: 80% em cima, 50% na lateral)
    };

    private static int top;
    private static int side;
    private static boolean loaded;

    private Theme() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tablet_inception").resolve("theme.properties");
    }

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(r);
            top = parse(p.getProperty("top"));
            side = parse(p.getProperty("side"));
        } catch (IOException | RuntimeException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel ler as cores do tablet: {}", e.toString());
        }
    }

    private static int parse(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            int v = Integer.parseInt(s.trim(), 16) & 0xFFFFFF;
            return v == 0xEEEEEE ? 0xFFFFFF : v;   // branco da versao anterior
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Properties p = new Properties();
            p.setProperty("top", top == 0 ? "" : String.format("%06X", top));
            p.setProperty("side", side == 0 ? "" : String.format("%06X", side));
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                p.store(w, "Cores do tablet (RGB em hexadecimal; vazio = padrao)");
            }
        } catch (IOException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel salvar as cores do tablet: {}", e.toString());
        }
    }

    public static int top() {
        return top;
    }

    public static int side() {
        return side;
    }

    public static boolean isWhite(int rgb) {
        return rgb == 0xFFFFFF;
    }

    public static boolean any() {
        return top != 0 || side != 0;
    }

    public static void setTop(int rgb) {
        top = rgb & 0xFFFFFF;
        save();
    }

    public static void setSide(int rgb) {
        side = rgb & 0xFFFFFF;
        save();
    }
}
