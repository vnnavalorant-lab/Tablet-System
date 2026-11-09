package com.example.tabletinception;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Configuracao em config/tablet_inception.json (criada automaticamente). */
public final class TabletConfig {
    public int width = 800;
    public int height = 570;
    public int memoryMb = 2048;
    public int fps = 30;
    public String username = "Inner";
    /** Caminho do java para o jogo interno. Vazio = o mesmo java do jogo atual. */
    public String javaPath = "";
    /** Argumentos JVM extras para o jogo interno. */
    public String extraJvmArgs = "";

    public static TabletConfig load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("tablet_inception.json");
        TabletConfig cfg = new TabletConfig();
        try {
            if (Files.exists(file)) {
                TabletConfig read = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), TabletConfig.class);
                if (read != null) {
                    cfg = read;
                }
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, gson.toJson(cfg), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel ler {}: {}", file, e.toString());
        }
        cfg.width = Math.max(320, Math.min(1920, cfg.width));
        cfg.height = Math.max(240, Math.min(1080, cfg.height));
        cfg.memoryMb = Math.max(512, cfg.memoryMb);
        cfg.fps = Math.max(5, Math.min(120, cfg.fps));
        if (cfg.username == null || cfg.username.isBlank()) cfg.username = "Inner";
        if (cfg.javaPath == null) cfg.javaPath = "";
        if (cfg.extraJvmArgs == null) cfg.extraJvmArgs = "";
        return cfg;
    }
}
