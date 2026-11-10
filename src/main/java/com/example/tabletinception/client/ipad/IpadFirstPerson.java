package com.example.tabletinception.client.ipad;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Desenha o iPad de lado, com as duas maos nas laterais, na visao em primeira pessoa. */
public final class IpadFirstPerson {
    /** Ponto (pixels do modelo do jogador) do centro da ponta da mao, com o braco sem rotacao. */
    private static final float ARM_HAND_X = 6f;
    private static final float ARM_HAND_Y = 12f;

    private IpadFirstPerson() {
    }

    public static void render(AbstractClientPlayerEntity player, float equipProgress, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light) {
        TabletClientState.Anim anim = TabletClientState.anim(TabletClientState.get(player.getUuid()));

        // iPad
        matrices.push();
        IpadPose.applyFirstPerson(matrices, equipProgress);
        IpadModel.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV, anim.name(), anim.time(), true);
        matrices.pop();

        // maos / bracos
        if (!player.isInvisible()) {
            PlayerEntityRenderer renderer =
                    (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
            IpadPose.suppressArmPose = true;
            try {
                drawArm(renderer, player, matrices, vcp, light, equipProgress, true);
                drawArm(renderer, player, matrices, vcp, light, equipProgress, false);
            } finally {
                IpadPose.suppressArmPose = false;
            }
        }
    }

    private static void drawArm(PlayerEntityRenderer renderer, AbstractClientPlayerEntity player, MatrixStack matrices,
                                VertexConsumerProvider vcp, int light, float equipProgress, boolean right) {
        Vector3f hand = IpadPose.firstPersonHand(equipProgress, right);
        Vector3f dir = IpadPose.firstPersonArmDir(right);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
        float ax = right ? -ARM_HAND_X : ARM_HAND_X; // centro do braco no espaco do modelo (x)

        matrices.push();
        matrices.translate(hand.x, hand.y, hand.z);
        matrices.multiply(q);
        matrices.translate(-ax / 16f, -ARM_HAND_Y / 16f, 0f);
        if (right) {
            renderer.renderRightArm(matrices, vcp, light, player);
        } else {
            renderer.renderLeftArm(matrices, vcp, light, player);
        }
        matrices.pop();
    }
}
