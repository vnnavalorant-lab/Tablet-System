package com.example.tabletinception.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * Etapa 1: tela do tablet mostrando uma textura dinamica de teste.
 * Nas proximas etapas, os pixels virao do Minecraft interno (processo filho).
 */
public class TabletScreen extends Screen {
    // Resolucao "virtual" do display do tablet (16:9).
    private static final int SCREEN_W = 427;
    private static final int SCREEN_H = 240;
    private static final int BEZEL = 6;

    private NativeImageBackedTexture texture;
    private Identifier textureId;

    public TabletScreen() {
        super(Text.translatable("screen.tablet_inception.tablet"));
    }

    @Override
    protected void init() {
        if (texture == null) {
            texture = new NativeImageBackedTexture(SCREEN_W, SCREEN_H, true);
            textureId = this.client.getTextureManager().registerDynamicTexture("tablet_inception_display", texture);
        }
    }

    @Override
    public void removed() {
        if (texture != null) {
            this.client.getTextureManager().destroyTexture(textureId);
            texture = null;
            textureId = null;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void updateTestPattern() {
        NativeImage image = texture.getImage();
        if (image == null) {
            return;
        }
        int t = (int) (Util.getMeasuringTimeMs() / 10L);
        for (int y = 0; y < SCREEN_H; y++) {
            for (int x = 0; x < SCREEN_W; x++) {
                int r = (x + t) & 0xFF;
                int g = (y + t / 2) & 0xFF;
                int b = ((x + y) / 2 - t) & 0xFF;
                // NativeImage usa o formato ABGR.
                image.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
        texture.upload();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        float scale = Math.min((this.width - 40) / (float) SCREEN_W, (this.height - 60) / (float) SCREEN_H);
        int drawW = Math.max(1, (int) (SCREEN_W * scale));
        int drawH = Math.max(1, (int) (SCREEN_H * scale));
        int x = (this.width - drawW) / 2;
        int y = (this.height - drawH) / 2;

        // Moldura do tablet
        context.fill(x - BEZEL, y - BEZEL, x + drawW + BEZEL, y + drawH + BEZEL, 0xFF1B1B1F);

        if (texture != null) {
            updateTestPattern();
            context.drawTexture(textureId, x, y, drawW, drawH, 0f, 0f, SCREEN_W, SCREEN_H, SCREEN_W, SCREEN_H);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y - BEZEL - 12, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
