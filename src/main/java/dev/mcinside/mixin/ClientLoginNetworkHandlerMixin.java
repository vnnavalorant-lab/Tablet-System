package dev.mcinside.mixin;

import dev.mcinside.inner.InnerSession;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** O login do cliente interno responde "não entendido" a consultas de login (evita mods que assumem o login principal). */
@Mixin(value = ClientLoginNetworkHandler.class, priority = 100)
public abstract class ClientLoginNetworkHandlerMixin {
    @Shadow @Final private ClientConnection connection;

    @Inject(method = "onQueryRequest", at = @At("HEAD"), cancellable = true)
    private void mci$query(LoginQueryRequestS2CPacket packet, CallbackInfo ci) {
        InnerSession s = InnerSession.ACTIVE;
        if (s != null && s.ownsConnection(this.connection)) {
            this.connection.send(new LoginQueryResponseC2SPacket(packet.getQueryId(), null));
            ci.cancel();
        }
    }
}
