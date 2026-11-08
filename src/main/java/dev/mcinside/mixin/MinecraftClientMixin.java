package dev.mcinside.mixin;

import dev.mcinside.inner.ClientAccess;
import dev.mcinside.inner.InnerSession;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements ClientAccess {
    @Shadow @Final @Mutable public WorldRenderer worldRenderer;
    @Shadow @Final @Mutable public ParticleManager particleManager;
    @Shadow @Final @Mutable public InGameHud inGameHud;
    @Shadow @Mutable private Framebuffer framebuffer;
    @Shadow public boolean skipGameRender;
    @Shadow public ClientWorld world;

    @Override public void mci$setWorldRenderer(WorldRenderer r) { this.worldRenderer = r; }
    @Override public void mci$setParticleManager(ParticleManager p) { this.particleManager = p; }
    @Override public void mci$setInGameHud(InGameHud h) { this.inGameHud = h; }
    @Override public void mci$setFramebuffer(Framebuffer f) { this.framebuffer = f; }
    @Override public void mci$setSkipGameRender(boolean b) { this.skipGameRender = b; }

    /** Telas abertas por código do jogo interno ficam dentro do tablet. */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mci$setScreen(Screen screen, CallbackInfo ci) {
        if (InnerSession.inContext()) {
            InnerSession.current().onInnerSetScreen(screen);
            ci.cancel();
        }
    }

    /** Entrar no mundo interno não pode mexer no mundo/tela principais. */
    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void mci$joinWorld(ClientWorld w, CallbackInfo ci) {
        if (InnerSession.inContext()) {
            this.world = w;
            this.worldRenderer.setWorld(w);
            this.particleManager.setWorld(w);
            ci.cancel();
        }
    }
}
