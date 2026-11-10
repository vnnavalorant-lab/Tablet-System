package com.example.tabletinception.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Textos traduzidos do mod (en_us e pt_br em assets/tablet_inception/lang).
 * As chaves usam o prefixo "tablet_inception." (ex.: {@code Lang.t("app.settings")}).
 * Idiomas sem traducao caem para o ingles (en_us).
 */
public final class Lang {
    private Lang() {
    }

    public static String t(String key, Object... args) {
        return Text.translatable("tablet_inception." + key, args).getString();
    }

    /** True se o idioma escolhido no jogo e o portugues (pt_br, pt_pt...). */
    public static boolean isPortuguese() {
        try {
            String code = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            return code != null && code.toLowerCase(java.util.Locale.ROOT).startsWith("pt");
        } catch (RuntimeException e) {
            return false;
        }
    }
}
