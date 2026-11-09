package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Papel de parede do tablet. A imagem escolhida e reduzida (maximo {@link #MAX_SIDE} px), salva em
 * config/tablet_inception/wallpaper.png e carregada como textura dinamica. Chamar so na thread de render.
 */
public final class Wallpaper {
    private static final int MAX_SIDE = 1280;
    private static final Identifier ID = new Identifier(TabletInceptionMod.MOD_ID, "wallpaper_dynamic");

    private static NativeImageBackedTexture texture;
    private static int width;
    private static int height;
    private static boolean loadedOnce;

    private Wallpaper() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tablet_inception").resolve("wallpaper.png");
    }

    public static boolean has() {
        return texture != null;
    }

    public static Identifier id() {
        return ID;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    /** Carrega o papel de parede salvo (uma unica vez por sessao). */
    public static void ensureLoaded() {
        if (loadedOnce) return;
        loadedOnce = true;
        Path f = file();
        if (Files.exists(f)) {
            try {
                apply(readScaled(f));
            } catch (IOException | RuntimeException e) {
                TabletInceptionMod.LOGGER.warn("Nao foi possivel carregar o papel de parede: {}", e.toString());
            }
        }
    }

    /** Define o papel de parede a partir de qualquer imagem (png, jpg, bmp, gif). */
    public static void setFromFile(Path src) throws IOException {
        loadedOnce = true;
        BufferedImage img = readScaled(src);
        apply(img);
        Path f = file();
        Files.createDirectories(f.getParent());
        ImageIO.write(img, "png", f.toFile());
    }

    /** Volta ao papel de parede padrao. */
    public static void clear() {
        loadedOnce = true;
        release();
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            TabletInceptionMod.LOGGER.warn("Nao foi possivel apagar o papel de parede: {}", e.toString());
        }
    }

    private static BufferedImage readScaled(Path src) throws IOException {
        BufferedImage in = ImageIO.read(src.toFile());
        if (in == null) throw new IOException("formato de imagem nao suportado");
        int w = in.getWidth();
        int h = in.getHeight();
        double s = Math.min(1.0, MAX_SIDE / (double) Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static void apply(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                ni.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);   // ABGR
            }
        }
        release();
        NativeImageBackedTexture t = new NativeImageBackedTexture(ni);
        MinecraftClient.getInstance().getTextureManager().registerTexture(ID, t);
        t.setFilter(true, false);
        texture = t;
        width = w;
        height = h;
    }

    private static void release() {
        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
            texture = null;
            width = 0;
            height = 0;
        }
    }
}
