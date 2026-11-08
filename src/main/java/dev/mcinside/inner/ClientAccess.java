package dev.mcinside.inner;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.gl.Framebuffer;

/** Setters de campos finais/privados do MinecraftClient, implementados pelo mixin. */
public interface ClientAccess {
    void mci$setWorldRenderer(WorldRenderer r);
    void mci$setParticleManager(ParticleManager p);
    void mci$setInGameHud(InGameHud h);
    void mci$setFramebuffer(Framebuffer f);
    void mci$setSkipGameRender(boolean b);
}
