package com.example.tabletinception.mixin;

import com.example.tabletinception.TabletInceptionMod;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Terceira pessoa: o iPad nao e desenhado "na mao" do jogo; quem desenha e o IpadFeatureRenderer. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class MixinHeldItemFeatureRenderer {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void tablet$skipIpad(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                 CallbackInfo ci) {
        if (stack.isOf(TabletInceptionMod.TABLET)) {
            ci.cancel();
        }
    }
}
