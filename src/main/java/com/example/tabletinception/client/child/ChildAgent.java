package com.example.tabletinception.client.child;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.channel.Channel;
import com.example.tabletinception.mixin.KeyboardInvoker;
import com.example.tabletinception.mixin.MouseInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/** Roda apenas no Minecraft interno (processo filho): captura frames e aplica a entrada recebida. */
public final class ChildAgent {
    private static final boolean CHILD = System.getProperty("tablet_inception.child") != null;

    private static Channel channel;
    private static ByteBuffer readBuf;
    private static long lastCaptureNs;
    private static long intervalNs = 33_000_000L;
    private static boolean stopping;
    private static boolean errorLogged;

    private ChildAgent() {}

    public static boolean isChild() {
        return CHILD;
    }

    public static void init() {
        try {
            channel = Channel.open(Path.of(System.getProperty("tablet_inception.child")));
            channel.setState(1);
            int fps = Math.max(1, Integer.getInteger("tablet_inception.fps", 30));
            intervalNs = 1_000_000_000L / fps;
            long pid = Long.getLong("tablet_inception.parentPid", -1L);
            if (pid > 0) {
                ProcessHandle.of(pid).ifPresent(h -> h.onExit().thenRun(ChildAgent::requestStop));
            }
            TabletInceptionMod.LOGGER.info("[Ipad-Scute6o] Agente do jogo interno iniciado.");
        } catch (Exception e) {
            TabletInceptionMod.LOGGER.error("[Ipad-Scute6o] Falha ao iniciar o agente", e);
        }
    }

    private static void requestStop() {
        if (stopping) return;
        stopping = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) mc.scheduleStop();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "tablet-inception-halt");
        t.setDaemon(true);
        t.start();
    }

    /** Inicio de cada frame: mantem a janela oculta e aplica a entrada vinda do tablet. */
    public static void onFrameStart(MinecraftClient mc) {
        long handle = mc.getWindow().getHandle();
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE) {
            GLFW.glfwHideWindow(handle);
        }
        if (channel == null) return;
        if (channel.shutdownRequested()) {
            requestStop();
        }
        channel.drainInput((type, a, b, c, d, d1, d2) -> {
            try {
                apply(mc, handle, type, a, b, c, d, d1, d2);
            } catch (Throwable t) {
                if (!errorLogged) {
                    errorLogged = true;
                    TabletInceptionMod.LOGGER.error("[Ipad-Scute6o] Erro ao aplicar entrada", t);
                }
            }
        });
    }

    private static void apply(MinecraftClient mc, long h, int type, int a, int b, int c, int d, double d1, double d2) {
        MouseInvoker mouse = (MouseInvoker) (Object) mc.mouse;
        KeyboardInvoker keyboard = (KeyboardInvoker) (Object) mc.keyboard;
        switch (type) {
            case Channel.IN_MOUSE_POS -> mouse.tablet$onCursorPos(h, d1, d2);
            case Channel.IN_MOUSE_DELTA -> mouse.tablet$onCursorPos(h, mouse.tablet$getX() + d1, mouse.tablet$getY() + d2);
            case Channel.IN_MOUSE_BUTTON -> mouse.tablet$onMouseButton(h, a, b, c);
            case Channel.IN_MOUSE_SCROLL -> mouse.tablet$onMouseScroll(h, d1, d2);
            case Channel.IN_KEY -> keyboard.tablet$onKey(h, a, b, c, d);
            case Channel.IN_CHAR -> keyboard.tablet$onChar(h, a, b);
            default -> { }
        }
    }

    /** Fim de cada frame: publica o framebuffer principal no canal compartilhado. */
    public static void onFrameEnd(MinecraftClient mc) {
        if (channel == null) return;
        try {
            channel.setCursorLocked(mc.mouse.isCursorLocked());
            channel.setWindowSize(mc.getWindow().getWidth(), mc.getWindow().getHeight());

            long now = System.nanoTime();
            if (now - lastCaptureNs < intervalNs) return;
            lastCaptureNs = now;

            Framebuffer fb = mc.getFramebuffer();
            int w = fb.textureWidth;
            int h = fb.textureHeight;
            if (w <= 0 || h <= 0 || w > Channel.MAX_W || h > Channel.MAX_H) return;

            int bytes = w * h * 4;
            if (readBuf == null || readBuf.capacity() < bytes) {
                readBuf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            }
            readBuf.clear();
            readBuf.limit(bytes);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fb.fbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readBuf);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);

            channel.writeFrame(readBuf, w, h);
            if (channel.state() < 2) {
                channel.setState(2);
            }
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                TabletInceptionMod.LOGGER.error("[Ipad-Scute6o] Erro ao capturar frame", t);
            }
        }
    }
}
