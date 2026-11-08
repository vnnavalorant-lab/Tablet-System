package dev.mcinside.client;

import org.lwjgl.glfw.GLFW;

/**
 * Entrada do ambiente interno via GLFW direto.
 * Não usamos Mouse.lockCursor() do Minecraft porque, com o cursor "travado" do jogo,
 * o delta do mouse também giraria o jogador principal. Aqui só a nossa tela lê o mouse.
 */
public final class InputManager {
    private long window;
    private boolean captured;
    private double lastX, lastY;
    private final double[] bx = new double[1], by = new double[1];

    public void capture(long windowHandle) {
        this.window = windowHandle;
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwGetCursorPos(window, bx, by);
        lastX = bx[0];
        lastY = by[0];
        captured = true;
    }

    public void release() {
        if (captured) GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        captured = false;
    }

    /** Delta do mouse desde a última chamada, em pixels de tela. */
    public double[] pollMouseDelta() {
        if (!captured) return new double[]{0, 0};
        GLFW.glfwGetCursorPos(window, bx, by);
        double[] d = {bx[0] - lastX, by[0] - lastY};
        lastX = bx[0];
        lastY = by[0];
        return d;
    }

    public boolean key(int glfwKey) {
        return captured && GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
    }
}
