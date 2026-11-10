package com.example.tabletinception.mixin;

import com.example.tabletinception.client.child.ChildAgent;
import com.example.tabletinception.client.ipad.IpadPose;
import com.example.tabletinception.client.ipad.TabletClientState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Terceira pessoa / outros jogadores: bracos segurando o iPad e a mao direita arrastando na tela. */
@Mixin(PlayerEntityModel.class)
public abstract class MixinPlayerEntityModel {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tablet$holdIpad(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                 float headYaw, float headPitch, CallbackInfo ci) {
        if (ChildAgent.isChild() || IpadPose.suppressArmPose) return;
        if (!(entity instanceof PlayerEntity player) || !IpadPose.isHolding(player)) return;

        TabletClientState.State st = TabletClientState.get(player.getUuid());
        float cx = 0.5f;
        float cy = 0.5f;
        float touch = 0f;
        if (st != null) {
            TabletClientState.smooth(st);
            cx = st.cx;
            cy = st.cy;
            touch = st.touch;
        }
        IpadPose.poseArms((PlayerEntityModel<?>) (Object) this, cx, cy, touch);
    }
}
