package com.example.tabletinception.mixin;

import com.example.tabletinception.client.child.ChildAgent;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MixinMinecraftClient {
    @Inject(method = "render(Z)V", at = @At("HEAD"))
    private void tablet$frameStart(boolean tick, CallbackInfo ci) {
        if (ChildAgent.isChild()) {
            ChildAgent.onFrameStart((MinecraftClient) (Object) this);
        }
    }

    @Inject(method = "render(Z)V", at = @At("TAIL"))
    private void tablet$frameEnd(boolean tick, CallbackInfo ci) {
        if (ChildAgent.isChild()) {
            ChildAgent.onFrameEnd((MinecraftClient) (Object) this);
        }
    }

    /** A janela oculta nunca ganha foco; o jogo interno precisa se considerar focado para travar o mouse. */
    @Inject(method = "isWindowFocused", at = @At("HEAD"), cancellable = true)
    private void tablet$focused(CallbackInfoReturnable<Boolean> cir) {
        if (ChildAgent.isChild()) {
            cir.setReturnValue(true);
        }
    }
}
