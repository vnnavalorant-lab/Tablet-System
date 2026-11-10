package com.example.tabletinception.client.ipad;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Desenha o iPad na frente do tronco dos jogadores (visto por outros jogadores e na terceira pessoa). */
public class IpadFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    public IpadFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator() || !IpadPose.isHolding(player)) return;
        TabletClientState.Anim anim = TabletClientState.anim(TabletClientState.get(player.getUuid()));

        matrices.push();
        this.getContextModel().body.rotate(matrices);   // acompanha o tronco (inclusive agachado)
        matrices.scale(1f / 16f, 1f / 16f, 1f / 16f);   // agora em pixels
        IpadPose.applyThirdPerson(matrices);
        IpadModel.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV, anim.name(), anim.time(), true);
        matrices.pop();
    }
}
