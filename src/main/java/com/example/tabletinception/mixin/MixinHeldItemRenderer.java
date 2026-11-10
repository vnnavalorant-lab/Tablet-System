package com.example.tabletinception.mixin;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.client.child.ChildAgent;
import com.example.tabletinception.client.ipad.IpadFirstPerson;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Primeira pessoa: com o iPad na mao, desenha ele de lado com as duas maos nas laterais. */
@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void tablet$ipad(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                             float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                             VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (ChildAgent.isChild()) return;

        if (item.isOf(TabletInceptionMod.TABLET)) {
            ci.cancel();
            boolean mainIsTablet = player.getMainHandStack().isOf(TabletInceptionMod.TABLET);
            // desenha uma vez so: pela mao principal; ou pela secundaria se a principal nao tem o iPad
            boolean owner = hand == Hand.MAIN_HAND || !mainIsTablet;
            if (owner) {
                IpadFirstPerson.render(player, equipProgress, matrices, vertexConsumers, light);
            }
            return;
        }

        // o iPad usa as duas maos: esconde o item/braco da outra mao
        ItemStack other = hand == Hand.MAIN_HAND ? player.getOffHandStack() : player.getMainHandStack();
        if (other.isOf(TabletInceptionMod.TABLET)) {
            ci.cancel();
        }
    }
}
