package dev.mcinside.mixin;

import dev.mcinside.inner.InnerServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dá ao mundo interno uma semente própria (também durante o construtor). */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mci$seed(CallbackInfoReturnable<Long> cir) {
        if (InnerServer.isInnerWorld(((ServerWorld) (Object) this).getRegistryKey())) cir.setReturnValue(InnerServer.seed);
    }
}
