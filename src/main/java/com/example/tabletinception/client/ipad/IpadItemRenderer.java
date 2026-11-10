package com.example.tabletinception.client.ipad;

import com.example.tabletinception.TabletInceptionMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Icone do iPad no inventario, no chao e em molduras (a mao do jogador e desenhada por IpadFirstPerson e
 * IpadFeatureRenderer). Tambem registra o renderer de terceira pessoa.
 */
public final class IpadItemRenderer {
    private IpadItemRenderer() {
    }

    public static void init() {
        BuiltinItemRendererRegistry.INSTANCE.register(TabletInceptionMod.TABLET, IpadItemRenderer::render);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new IpadFeatureRenderer(playerRenderer));
            }
        });
    }

    private static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light, int overlay) {
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        switch (mode) {
            case GUI -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
                matrices.scale(0.8f, 0.8f, 0.8f);
            }
            case GROUND -> {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
                matrices.scale(0.5f, 0.5f, 0.5f);
            }
            default -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
                matrices.scale(0.7f, 0.7f, 0.7f);
            }
        }
        matrices.translate(-IpadModel.CENTER_X / 16f, -IpadModel.CENTER_Y / 16f, -IpadModel.CENTER_Z / 16f);
        IpadModel.render(matrices, vcp, light, overlay, "off", 0.25f, false);
        matrices.pop();
    }
}
