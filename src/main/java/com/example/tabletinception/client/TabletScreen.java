package com.example.tabletinception.client;

import com.example.tabletinception.channel.Channel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela do tablet: mostra o Minecraft interno e envia teclado/mouse para ele.
 *
 *  - Clique na tela do tablet: captura a entrada.
 *  - Ctrl direito: solta a entrada. Com a entrada solta, Esc fecha o tablet.
 */
public class TabletScreen extends Screen {
    private static final int BEZEL = 6;
    private static final int TOP = 22;
    private static final int BOTTOM = 56;

    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private int texW;
    private int texH;
    private int lastSeq;
    private final int[] meta = new int[3];

    private boolean captured;
    private boolean cursorDisabled;
    private boolean lastValid;
    private double lastMx;
    private double lastMy;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> pressedButtons = new HashSet<>();

    private ButtonWidget powerButton;
    private boolean autoStarted;

    private int dx;
    private int dy;
    private int dw;
    private int dh;

    public TabletScreen() {
        super(Text.literal("Tablet"));
    }

    @Override
    protected void init() {
        powerButton = this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Ligar"), b -> onPower())
                        .dimensions(this.width / 2 - 60, this.height - 28, 120, 20)
                        .build());
        if (!autoStarted && ChildManager.status() == ChildManager.Status.OFF) {
            autoStarted = true;
            ChildManager.start();
        }
    }

    private void onPower() {
        switch (ChildManager.status()) {
            case OFF, DEAD -> ChildManager.start();
            case STARTING, RUNNING -> {
                uncapture();
                ChildManager.stop();
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ render

    private void computeLayout() {
        double aspect = texW > 0 ? texW / (double) texH : 16.0 / 9.0;
        int availW = this.width - 40;
        int availH = this.height - TOP - BOTTOM;
        if (availW / aspect > availH) {
            dh = Math.max(1, availH);
            dw = Math.max(1, (int) (dh * aspect));
        } else {
            dw = Math.max(1, availW);
            dh = Math.max(1, (int) (dw / aspect));
        }
        dx = (this.width - dw) / 2;
        dy = TOP + (availH - dh) / 2;
    }

    private void pollFrame(ChildManager.Status st) {
        Channel ch = ChildManager.channel();
        if (st != ChildManager.Status.RUNNING || ch == null) {
            lastSeq = 0;
            return;
        }
        ByteBuffer buf = ChildManager.frameBuffer();
        if (ch.readFrame(buf, meta, lastSeq)) {
            lastSeq = meta[2];
            ensureTexture(meta[0], meta[1]);
            texture.bindTexture();
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, meta[0], meta[1],
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        }
    }

    private void ensureTexture(int w, int h) {
        if (texture != null && w == texW && h == texH) return;
        destroyTexture();
        texture = new NativeImageBackedTexture(w, h, false);
        textureId = this.client.getTextureManager().registerDynamicTexture("tablet_inception_display", texture);
        texture.setFilter(true, false);
        texW = w;
        texH = h;
    }

    private void destroyTexture() {
        if (texture != null) {
            this.client.getTextureManager().destroyTexture(textureId);
            texture = null;
            textureId = null;
            texW = 0;
            texH = 0;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        ChildManager.Status st = ChildManager.status();
        pollFrame(st);
        computeLayout();

        ctx.fill(dx - BEZEL, dy - BEZEL, dx + dw + BEZEL, dy + dh + BEZEL, 0xFF1B1B1F);

        if (st == ChildManager.Status.RUNNING && texture != null) {
            ctx.drawTexture(textureId, dx, dy, dw, dh, 0f, 0f, texW, texH, texW, texH);
        } else {
            ctx.fill(dx, dy, dx + dw, dy + dh, 0xFF000000);
            drawPlaceholder(ctx, st);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 6, 0xFFFFFF);

        String hint = captured
                ? "Ctrl direito: soltar o mouse e o teclado"
                : (st == ChildManager.Status.RUNNING
                        ? "Clique na tela para controlar  |  Esc: fechar o tablet"
                        : "Esc: fechar o tablet");
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, dy + dh + BEZEL + 6, 0xA0A0A0);

        powerButton.setMessage(Text.literal(
                st == ChildManager.Status.STARTING || st == ChildManager.Status.RUNNING ? "Desligar" : "Ligar"));

        super.render(ctx, mouseX, mouseY, delta);
        updateCursorMode(st);
    }

    private void drawPlaceholder(DrawContext ctx, ChildManager.Status st) {
        int cx = dx + dw / 2;
        int cy = dy + dh / 2;
        switch (st) {
            case OFF -> ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Minecraft interno desligado"), cx, cy - 4, 0xFFFFFF);
            case STARTING -> {
                int dots = (int) ((System.currentTimeMillis() / 400) % 4);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Iniciando o Minecraft interno" + ".".repeat(dots)), cx, cy - 12, 0xFFFFFF);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Isso pode levar um minuto na primeira vez."), cx, cy + 2, 0xA0A0A0);
            }
            case DEAD -> ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("O Minecraft interno encerrou. Veja tablet_inception/child.log"), cx, cy - 4, 0xFF8080);
            default -> { }
        }
        String err = ChildManager.lastError();
        if (!err.isEmpty()) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(err), Math.max(40, dw - 20));
            int y = cy + 16;
            for (OrderedText line : lines) {
                ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, y, 0xFF8080);
                y += 10;
            }
        }
    }

    private void updateCursorMode(ChildManager.Status st) {
        Channel ch = ChildManager.channel();
        boolean want = captured && st == ChildManager.Status.RUNNING && ch != null && ch.cursorLocked();
        if (want != cursorDisabled) {
            cursorDisabled = want;
            lastValid = false;
            GLFW.glfwSetInputMode(this.client.getWindow().getHandle(), GLFW.GLFW_CURSOR,
                    want ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    // ------------------------------------------------------------------ entrada

    private boolean running() {
        return ChildManager.status() == ChildManager.Status.RUNNING && ChildManager.channel() != null;
    }

    private boolean inside(double mx, double my) {
        return mx >= dx && mx < dx + dw && my >= dy && my < dy + dh;
    }

    private void sendAbsolute(double mx, double my) {
        Channel ch = ChildManager.channel();
        int ww = ch.windowWidth() > 0 ? ch.windowWidth() : 854;
        int wh = ch.windowHeight() > 0 ? ch.windowHeight() : 480;
        double x = Math.max(0, Math.min(ww - 1, (mx - dx) / dw * ww));
        double y = Math.max(0, Math.min(wh - 1, (my - dy) / dh * wh));
        ch.sendInput(Channel.IN_MOUSE_POS, 0, 0, 0, 0, x, y);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!captured || !running()) {
            lastValid = false;
            return;
        }
        if (cursorDisabled) {
            Window w = this.client.getWindow();
            if (lastValid) {
                double sx = w.getWidth() / (double) w.getScaledWidth();
                double sy = w.getHeight() / (double) w.getScaledHeight();
                ChildManager.channel().sendInput(Channel.IN_MOUSE_DELTA, 0, 0, 0, 0,
                        (mx - lastMx) * sx, (my - lastMy) * sy);
            }
            lastMx = mx;
            lastMy = my;
            lastValid = true;
        } else {
            sendAbsolute(mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (running()) {
            if (captured && cursorDisabled) {
                ChildManager.channel().sendInput(Channel.IN_MOUSE_BUTTON, button, 1, 0, 0, 0, 0);
                pressedButtons.add(button);
                return true;
            }
            if (inside(mx, my)) {
                captured = true;
                sendAbsolute(mx, my);
                ChildManager.channel().sendInput(Channel.IN_MOUSE_BUTTON, button, 1, 0, 0, 0, 0);
                pressedButtons.add(button);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pressedButtons.remove(button) && running()) {
            ChildManager.channel().sendInput(Channel.IN_MOUSE_BUTTON, button, 0, 0, 0, 0, 0);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dX, double dY) {
        return captured || super.mouseDragged(mx, my, button, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (captured && running()) {
            ChildManager.channel().sendInput(Channel.IN_MOUSE_SCROLL, 0, 0, 0, 0, 0, amount);
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (captured && running()) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
                uncapture();
                return true;
            }
            int action = pressedKeys.add(keyCode) ? 1 : 2;
            ChildManager.channel().sendInput(Channel.IN_KEY, keyCode, scanCode, action, modifiers, 0, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (pressedKeys.remove(keyCode) && running()) {
            ChildManager.channel().sendInput(Channel.IN_KEY, keyCode, scanCode, 0, modifiers, 0, 0);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (captured && running()) {
            ChildManager.channel().sendInput(Channel.IN_CHAR, chr, modifiers, 0, 0, 0, 0);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    /** Solta teclas/botoes pressionados e devolve o cursor ao jogo principal. */
    private void uncapture() {
        Channel ch = ChildManager.channel();
        if (ch != null && ChildManager.status() != ChildManager.Status.OFF) {
            for (int key : pressedKeys) {
                ch.sendInput(Channel.IN_KEY, key, 0, 0, 0, 0, 0);
            }
            for (int button : pressedButtons) {
                ch.sendInput(Channel.IN_MOUSE_BUTTON, button, 0, 0, 0, 0, 0);
            }
        }
        pressedKeys.clear();
        pressedButtons.clear();
        captured = false;
        if (cursorDisabled) {
            cursorDisabled = false;
            GLFW.glfwSetInputMode(this.client.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        lastValid = false;
    }

    @Override
    public void removed() {
        uncapture();
        destroyTexture();
    }
}
