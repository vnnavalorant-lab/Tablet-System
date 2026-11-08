package dev.mcinside.inner;

import net.minecraft.client.input.Input;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Entrada do jogador interno, lida direto do GLFW (não mexe nas KeyBindings do jogo principal). */
public final class TabletInput extends Input {
    private final long window;
    public boolean enabled = true;

    public TabletInput(long window) { this.window = window; }

    private boolean k(int code) { return enabled && InputUtil.isKeyPressed(window, code); }

    @Override
    public void tick(boolean slowDown, float slowDownFactor) {
        this.pressingForward = k(GLFW.GLFW_KEY_W);
        this.pressingBack = k(GLFW.GLFW_KEY_S);
        this.pressingLeft = k(GLFW.GLFW_KEY_A);
        this.pressingRight = k(GLFW.GLFW_KEY_D);
        this.movementForward = (pressingForward == pressingBack) ? 0f : (pressingForward ? 1f : -1f);
        this.movementSideways = (pressingLeft == pressingRight) ? 0f : (pressingLeft ? 1f : -1f);
        this.jumping = k(GLFW.GLFW_KEY_SPACE);
        this.sneaking = k(GLFW.GLFW_KEY_LEFT_SHIFT);
        if (slowDown) { this.movementSideways *= slowDownFactor; this.movementForward *= slowDownFactor; }
    }
}
