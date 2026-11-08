package dev.mcinside.mixin;

import dev.mcinside.inner.InnerSession;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkThreadUtils.class)
public abstract class NetworkThreadUtilsMixin {
    /** Pacotes do cliente interno são tratados na thread principal, dentro do contexto interno. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mci$force(Packet packet, PacketListener listener, ThreadExecutor engine, CallbackInfo ci) throws OffThreadException {
        InnerSession s = InnerSession.ACTIVE;
        if (s == null || InnerSession.inContext() || !s.owns(listener)) return;
        engine.execute(() -> {
            if (!s.isRunning()) return;
            s.inContext(() -> {
                try { packet.apply(listener); } catch (Throwable t) { dev.mcinside.MinecraftInside.LOGGER.warn("Pacote interno falhou", t); }
            });
        });
        throw OffThreadException.INSTANCE;
    }
}
