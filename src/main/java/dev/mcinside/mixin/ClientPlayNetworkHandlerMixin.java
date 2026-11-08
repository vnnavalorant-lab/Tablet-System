package dev.mcinside.mixin;

import dev.mcinside.inner.InnerSession;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    /** Desconexão do jogo interno nunca pode derrubar o mundo principal. */
    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void mci$disc(Text reason, CallbackInfo ci) {
        InnerSession s = InnerSession.ACTIVE;
        if (s != null && s.owns(this)) { s.onInnerDisconnected(); ci.cancel(); }
    }
}
