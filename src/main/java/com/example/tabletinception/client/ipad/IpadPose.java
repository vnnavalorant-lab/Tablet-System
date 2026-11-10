package com.example.tabletinception.client.ipad;

import com.example.tabletinception.TabletInceptionMod;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Geometria de como o iPad e segurado (primeira e terceira pessoa) e calculo da pose dos bracos.
 *
 * TODAS as constantes de posicao/tamanho ficam aqui: se algo ficar um pouco torto no jogo,
 * e so ajustar estes numeros.
 */
public final class IpadPose {
    // ---- terceira pessoa (espaco do corpo do jogador: pixels, Y para baixo, frente = -Z) ----
    /** Tamanho do iPad em relacao ao modelo original (18 px de largura). */
    public static final float TP_SCALE = 0.6f;
    /** Centro do iPad em relacao ao topo do tronco. */
    public static final float TP_Y = 7f;
    public static final float TP_Z = -9f;
    /** Inclinacao do iPad (graus): a tela olha para o rosto do jogador. */
    public static final float TP_TILT = 35f;

    // ---- primeira pessoa (espaco da camera: blocos, Y para cima, a camera olha para -Z) ----
    public static final float FP_SCALE = 0.5f;
    public static final float FP_Y = -0.34f;
    public static final float FP_Z = -0.66f;
    public static final float FP_TILT = -18f;

    /** Folga das maos para fora da borda do iPad (pixels do modelo). */
    private static final float GRIP_OUT = 0.5f;
    /** Altura da tela do iPad (pixels do modelo) e largura util. */
    private static final float SCREEN_W = 16f;
    private static final float SCREEN_H = 11f;
    /** Quanto a ponta do dedo fica "flutuando" a frente da tela (pixels do modelo, -Z = frente da tela). */
    private static final float HOVER_Z = -1.6f;

    /** Ligado enquanto desenhamos os bracos da 1a pessoa (para o mixin do modelo nao mexer neles). */
    public static volatile boolean suppressArmPose;

    private IpadPose() {
    }

    public static boolean isHolding(PlayerEntity p) {
        return p.getMainHandStack().isOf(TabletInceptionMod.TABLET) || p.getOffHandStack().isOf(TabletInceptionMod.TABLET);
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    // ------------------------------------------------------------------ terceira pessoa

    /** Matriz: espaco do modelo do iPad (px) -> espaco do corpo (px). */
    public static Matrix4f thirdPersonMatrix() {
        return new Matrix4f()
                .translate(0, TP_Y, TP_Z)
                .rotateX(rad(TP_TILT))
                .rotateX((float) Math.PI)
                .scale(TP_SCALE)
                .translate(-IpadModel.CENTER_X, -IpadModel.CENTER_Y, -IpadModel.CENTER_Z);
    }

    /**
     * Mesma transformacao da matriz acima, aplicada na pilha. A pilha deve estar em pixels (ex.: scale(1/16)
     * logo depois de body.rotate). No fim a escala volta a "unidades do IpadModel.render" (px/16).
     */
    public static void applyThirdPerson(MatrixStack m) {
        m.translate(0, TP_Y, TP_Z);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(TP_TILT));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
        m.scale(TP_SCALE, TP_SCALE, TP_SCALE);
        m.translate(-IpadModel.CENTER_X, -IpadModel.CENTER_Y, -IpadModel.CENTER_Z);
        m.scale(16f, 16f, 16f);
    }

    /**
     * Aponta o braco (do modelo do jogador) para um ponto no espaco raiz do modelo.
     * pitch 0 = braco para baixo, -PI/2 = braco para a frente.
     */
    private static void aim(ModelPart arm, Vector3f target) {
        Vector3f d = new Vector3f(target.x - arm.pivotX, target.y - arm.pivotY, target.z - arm.pivotZ);
        float len = d.length();
        if (len < 1e-4f) return;
        d.div(len);
        float pitch = -(float) Math.acos(Math.max(-1f, Math.min(1f, d.y)));
        float s = (float) Math.sin(pitch);
        float yaw = Math.abs(s) < 1e-4f ? 0f : (float) Math.atan2(d.x / s, d.z / s);
        arm.pitch = pitch;
        arm.yaw = yaw;
        arm.roll = 0f;
    }

    /**
     * Coloca os dois bracos segurando o iPad pelas laterais. A mao direita vai ate o ponto do cursor
     * (cx, cy em 0..1, visto de quem usa) quando {@code touch} > 0.
     */
    public static void poseArms(PlayerEntityModel<?> model, float cx, float cy, float touch) {
        ModelPart body = model.body;
        Matrix4f toRoot = new Matrix4f()
                .translate(body.pivotX, body.pivotY, body.pivotZ)
                .rotateZYX(body.roll, body.yaw, body.pitch)
                .mul(thirdPersonMatrix());

        float edge = IpadModel.HALF_W + GRIP_OUT;
        Vector3f restR = toRoot.transformPosition(new Vector3f(-edge, IpadModel.CENTER_Y, 0));
        Vector3f restL = toRoot.transformPosition(new Vector3f(edge, IpadModel.CENTER_Y, 0));

        Vector3f tgtR = restR;
        if (touch > 0.01f) {
            // visto de quem usa: esquerda da tela = +X do modelo; topo da tela = +Y do modelo
            float lx = (0.5f - cx) * SCREEN_W;
            float ly = (1f - cy) * SCREEN_H;
            Vector3f touchPt = toRoot.transformPosition(new Vector3f(lx, ly, HOVER_Z));
            tgtR = new Vector3f(restR).lerp(touchPt, Math.min(1f, touch));
        }
        aim(model.rightArm, tgtR);
        aim(model.leftArm, restL);
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
    }

    // ------------------------------------------------------------------ primeira pessoa

    /** Matriz: modelo do iPad (px) -> espaco da camera (blocos). */
    public static Matrix4f firstPersonMatrix(float equipProgress) {
        return new Matrix4f()
                .translate(0, FP_Y - 0.9f * equipProgress, FP_Z)
                .rotateX(rad(FP_TILT))
                .rotateY((float) Math.PI)
                .scale(FP_SCALE / 16f)
                .translate(-IpadModel.CENTER_X, -IpadModel.CENTER_Y, -IpadModel.CENTER_Z);
    }

    /** Aplica na pilha (em blocos, espaco da camera) a mesma transformacao de {@link #firstPersonMatrix}. */
    public static void applyFirstPerson(MatrixStack m, float equipProgress) {
        m.translate(0, FP_Y - 0.9f * equipProgress, FP_Z);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FP_TILT));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        m.scale(FP_SCALE / 16f, FP_SCALE / 16f, FP_SCALE / 16f);
        m.translate(-IpadModel.CENTER_X, -IpadModel.CENTER_Y, -IpadModel.CENTER_Z);
        m.scale(16f, 16f, 16f);
    }

    /** Ponto (camera, blocos) onde a mao fica: meio da lateral do iPad. */
    public static Vector3f firstPersonHand(float equipProgress, boolean right) {
        float edge = IpadModel.HALF_W + GRIP_OUT;
        // no espaco da camera a lateral DIREITA corresponde a -X do modelo (o modelo esta girado 180 graus)
        return firstPersonMatrix(equipProgress).transformPosition(
                new Vector3f(right ? -edge : edge, IpadModel.CENTER_Y, 0));
    }

    /** Direcao (camera) do ombro ate a mao: vem de baixo, para a frente e um pouco para dentro. */
    public static Vector3f firstPersonArmDir(boolean right) {
        return new Vector3f(right ? -0.12f : 0.12f, 0.5f, -0.86f).normalize();
    }
}
