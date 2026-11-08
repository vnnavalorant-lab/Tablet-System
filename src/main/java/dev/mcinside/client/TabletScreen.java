package dev.mcinside.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcinside.inner.ClientAccess;
import dev.mcinside.inner.InnerSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do tablet. Mostra o framebuffer do Minecraft interno (renderizado pelo próprio renderizador do jogo, Fancy)
 * e repassa mouse/teclado. Enquanto aberta, o mundo principal não é desenhado (economiza o custo do render duplo).
 */
public class TabletScreen extends Screen implements FakeScreens.Host {
    private InnerSession session;
    private boolean ready, captured, loading;
    private int sx, sy, sw, sh;
    private double lastX, lastY;
    private final double[] bx = new double[1], by = new double[1];

    public TabletScreen() { super(Text.literal("Tablet")); }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        if (session == null) {
            session = new InnerSession();
            ready = session.prepare();
            if (ready) {
                ((ClientAccess) client).mci$setSkipGameRender(true);
                session.setScreen(new FakeScreens.Title(this));
            } else {
                client.setScreen(null);
                return;
            }
        }
        Window w = client.getWindow();
        double aspect = w.getFramebufferWidth() / (double) w.getFramebufferHeight();
        int m = 14;
        int maxW = width - 2 * m, maxH = height - 2 * m;
        sw = (int) Math.min(maxW, maxH * aspect);
        sh = (int) (sw / aspect);
        sx = (width - sw) / 2;
        sy = (height - sh) / 2;
    }

    // ---------------------------------------------------------- Host

    @Override public void play() {
        if (!session.connect()) { session.setScreen(new FakeScreens.Message(this, "Mundo interno indisponivel (so em um mundo single-player)")); return; }
        loading = true;
        session.setScreen(new FakeScreens.Loading(this));
    }
    @Override public void quitTablet() { client.setScreen(null); }
    @Override public void backToTitle() { releaseMouse(); session.disconnect(); session.setScreen(new FakeScreens.Title(this)); }
    @Override public void resume() { session.setScreen(null); }
    @Override public void openSelect() { session.setScreen(new FakeScreens.Select(this)); }
    @Override public void openTitle() { session.setScreen(new FakeScreens.Title(this)); }

    // ---------------------------------------------------------- tick / render

    public void tickInner() {
        if (!ready || session == null) return;
        session.tick();
        if (loading && session.isJoined()) { loading = false; session.setScreen(null); }
        if (session.isRunning() == false && session.world() == null && loading) { loading = false; }
        boolean wantCapture = session.isJoined() && session.innerScreen() == null;
        if (wantCapture && !captured) captureMouse();
        else if (!wantCapture && captured) releaseMouse();
    }

    private void captureMouse() {
        long h = client.getWindow().getHandle();
        GLFW.glfwSetInputMode(h, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwGetCursorPos(h, bx, by);
        lastX = bx[0]; lastY = by[0];
        captured = true;
    }

    private void releaseMouse() {
        if (captured) {
            long h = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(h, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        captured = false;
        if (session != null) { session.leftHeld = false; session.rightHeld = false; }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF0A0A0C);
        if (!ready) return;
        ctx.fill(sx - 10, sy - 10, sx + sw + 10, sy + sh + 10, 0xFF26262B);
        ctx.fill(sx - 2, sy - 2, sx + sw + 2, sy + sh + 2, 0xFF000000);

        if (captured) {
            long h = client.getWindow().getHandle();
            GLFW.glfwGetCursorPos(h, bx, by);
            double dx = bx[0] - lastX, dy = by[0] - lastY;
            lastX = bx[0]; lastY = by[0];
            double s = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double k = s * s * s * 8.0;
            session.look(dx * k, dy * k * (client.options.getInvertYMouse().getValue() ? -1 : 1));
        }
        Window w = client.getWindow();
        session.innerMouseX = (mouseX - sx) / (double) sw * w.getScaledWidth();
        session.innerMouseY = (mouseY - sy) / (double) sh * w.getScaledHeight();

        float td = client.getTickDelta();
        session.renderFrame(ctx, td);
        if (session.framebuffer() == null) return;

        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, session.framebuffer().getColorAttachment());
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bb.vertex(m, sx, sy + sh, 0f).texture(0f, 0f).next();
        bb.vertex(m, sx + sw, sy + sh, 0f).texture(1f, 0f).next();
        bb.vertex(m, sx + sw, sy, 0f).texture(1f, 1f).next();
        bb.vertex(m, sx, sy, 0f).texture(0f, 1f).next();
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    // ---------------------------------------------------------- input

    private double ix(double mx) { return (mx - sx) / sw * client.getWindow().getScaledWidth(); }
    private double iy(double my) { return (my - sy) / sh * client.getWindow().getScaledHeight(); }
    private Screen inner() { return session == null ? null : session.innerScreen(); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!ready) return false;
        Screen in = inner();
        if (in != null) { session.inContext(() -> in.mouseClicked(ix(mx), iy(my), button)); return true; }
        if (!session.isJoined()) return true;
        if (button == 0) { session.leftHeld = true; session.attackPressed(); }
        else if (button == 1) { session.rightHeld = true; session.usePressed(); }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!ready) return false;
        if (button == 0) session.leftHeld = false;
        if (button == 1) session.rightHeld = false;
        Screen in = inner();
        if (in != null) session.inContext(() -> in.mouseReleased(ix(mx), iy(my), button));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Screen in = inner();
        if (in != null) { session.inContext(() -> in.mouseDragged(ix(mx), iy(my), button, dx, dy)); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!ready) return false;
        Screen in = inner();
        if (in != null) { session.inContext(() -> in.mouseScrolled(ix(mx), iy(my), amount)); return true; }
        if (session.isJoined()) session.scroll(amount);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!ready) return false;
        Screen in = inner();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (in instanceof FakeScreens.Title) { client.setScreen(null); return true; }
            if (in instanceof FakeScreens.Select) { openTitle(); return true; }
            if (in instanceof FakeScreens.Pause) { resume(); return true; }
            if (in != null && session.isJoined()) { session.setScreen(null); return true; }
            if (in == null && session.isJoined()) { session.setScreen(new FakeScreens.Pause(this)); return true; }
            return true;
        }
        if (in != null) { session.inContext(() -> in.keyPressed(key, scan, mods)); return true; }
        if (!session.isJoined()) return true;
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) session.selectSlot(key - GLFW.GLFW_KEY_1);
        else if (key == GLFW.GLFW_KEY_E) session.openInventory();
        else if (key == GLFW.GLFW_KEY_Q) session.drop((mods & GLFW.GLFW_MOD_CONTROL) != 0);
        else if (key == GLFW.GLFW_KEY_F) session.swapHands();
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        Screen in = inner();
        if (in != null) session.inContext(() -> in.keyReleased(key, scan, mods));
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        Screen in = inner();
        if (in != null) { session.inContext(() -> in.charTyped(c, mods)); return true; }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        Screen in = inner();
        if (in != null) session.inContext(() -> in.mouseMoved(ix(mx), iy(my)));
    }

    @Override
    public void removed() {
        releaseMouse();
        if (session != null) { session.close(); session = null; }
        ((ClientAccess) client).mci$setSkipGameRender(false);
        ready = false;
    }
}
