package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.channel.Channel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tablet: tela inicial com o icone do Minecraft. Ao tocar no icone, abre o Minecraft interno.
 *
 * Botoes de navegacao (coluna da direita):
 *   triangulo = voltar (no app volta para a tela inicial; na tela inicial fecha o tablet)
 *   circulo   = tela inicial (o Minecraft continua rodando em segundo plano)
 *   quadrado  = fechar o app (desliga o Minecraft interno)
 *
 * No app: clique na tela para capturar mouse/teclado, Ctrl direito solta, Esc (solto) fecha o tablet.
 */
public class TabletScreen extends Screen {
    private static final Identifier PANEL = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/tablet_panel.png");
    private static final Identifier ICON = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/minecraft_icon.png");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // Medidas da imagem do painel (em pixels da textura)
    private static final int PANEL_W = 232;
    private static final int PANEL_H = 170;
    private static final int SCREEN_X = 10;       // area da tela
    private static final int SCREEN_Y = 9;
    private static final int STATUS_H = 9;        // barra de status
    private static final int HOME_Y = 18;         // inicio da area de apps
    private static final int HOME_W = 198;        // largura da area de apps (sem a coluna de navegacao)
    private static final int HOME_H = 141;
    private static final int NAV_X = 208;         // coluna de navegacao
    private static final int NAV_W = 14;
    private static final int ICON_SIZE = 20;

    private enum View { HOME, APP }

    private View view = View.HOME;

    // layout (em pixels da GUI)
    private int s = 1;
    private int ox;
    private int oy;
    private int ax;
    private int ay;
    private int aw;
    private int ah;
    // retangulo onde o frame do jogo e desenhado
    private int dx;
    private int dy;
    private int dw;
    private int dh;
    // celula do icone na tela inicial
    private int cellX;
    private int cellY;
    private int cellW;
    private int cellH;

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

    public TabletScreen() {
        super(Text.literal("Tablet"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private void computeLayout(ChildManager.Status st) {
        s = Math.max(1, Math.min((this.width - 16) / PANEL_W, (this.height - 30) / PANEL_H));
        ox = (this.width - PANEL_W * s) / 2;
        oy = (this.height - PANEL_H * s) / 2;
        ax = ox + SCREEN_X * s;
        ay = oy + HOME_Y * s;
        aw = HOME_W * s;
        ah = HOME_H * s;

        if (view == View.APP && st == ChildManager.Status.RUNNING && texW > 0) {
            double aspect = texW / (double) texH;
            if (aw / aspect > ah) {
                dh = ah;
                dw = Math.max(1, (int) (dh * aspect));
            } else {
                dw = aw;
                dh = Math.max(1, (int) (dw / aspect));
            }
            dx = ax + (aw - dw) / 2;
            dy = ay + (ah - dh) / 2;
        } else {
            dx = ax;
            dy = ay;
            dw = aw;
            dh = ah;
        }

        int labelScale = labelScale();
        int labelW = this.textRenderer.getWidth("Minecraft") * labelScale;
        int iconPx = ICON_SIZE * s;
        cellW = Math.max(iconPx + 8 * s, labelW + 6 * s);
        cellH = iconPx + 3 * s + 8 * labelScale + 4 * s;
        cellX = ax + 8 * s;
        cellY = ay + 8 * s;
    }

    private int labelScale() {
        return Math.max(1, Math.round(s * 0.6f));
    }

    private int statusTextScale() {
        return Math.max(1, Math.min(Math.round(s * 0.75f), (STATUS_H * s) / 8));
    }

    // ------------------------------------------------------------------ frames

    private void pollFrame(ChildManager.Status st) {
        Channel ch = ChildManager.channel();
        if (view != View.APP || st != ChildManager.Status.RUNNING || ch == null) {
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

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        ChildManager.Status st = ChildManager.status();
        computeLayout(st);
        pollFrame(st);
        if (view == View.APP) {
            computeLayout(st); // o tamanho do frame pode ter mudado
        }

        // Moldura + fundo do tablet
        ctx.drawTexture(PANEL, ox, oy, PANEL_W * s, PANEL_H * s, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        drawStatusBar(ctx);

        if (view == View.HOME) {
            drawHome(ctx, st, mouseX, mouseY);
        } else {
            drawApp(ctx, st);
        }

        // Dica abaixo do tablet
        int hintY = oy + PANEL_H * s + 6;
        if (hintY + 9 <= this.height) {
            String hint = view == View.APP
                    ? (captured ? "Ctrl direito: soltar mouse e teclado"
                                : "Clique na tela para controlar  |  Esc: fechar o tablet")
                    : "Toque no icone para abrir  |  Esc: fechar o tablet";
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, hintY, 0xA0A0A0);
        }

        super.render(ctx, mouseX, mouseY, delta);
        updateCursorMode(st);
    }

    private void drawScaledText(DrawContext ctx, String text, int x, int y, int scale, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(text), 0, 0, color);
        m.pop();
    }

    private void drawStatusBar(DrawContext ctx) {
        int ts = statusTextScale();
        int y = oy + SCREEN_Y * s + (STATUS_H * s - 8 * ts) / 2 + ts / 2;
        drawScaledText(ctx, LocalTime.now().format(CLOCK), ox + (SCREEN_X + 4) * s, y, ts, 0xFFFFFFFF);
    }

    private void drawHome(DrawContext ctx, ChildManager.Status st, int mouseX, int mouseY) {
        boolean hover = inIconCell(mouseX, mouseY);
        if (hover) {
            ctx.fill(cellX, cellY, cellX + cellW, cellY + cellH, 0x33FFFFFF);
        }

        int iconPx = ICON_SIZE * s;
        int iconX = cellX + (cellW - iconPx) / 2;
        ctx.drawTexture(ICON, iconX, cellY + 2 * s, iconPx, iconPx, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        int ls = labelScale();
        int labelW = this.textRenderer.getWidth("Minecraft") * ls;
        int labelX = cellX + (cellW - labelW) / 2;
        int labelY = cellY + 2 * s + iconPx + 2 * s;
        drawScaledText(ctx, "Minecraft", labelX, labelY, ls, 0xFFFFFFFF);

        // Pontinho "app em execucao"
        if (st == ChildManager.Status.STARTING || st == ChildManager.Status.RUNNING) {
            int dot = Math.max(2, s);
            int cx = cellX + cellW / 2 - dot / 2;
            int cy = cellY + cellH - dot - Math.max(1, s / 2);
            ctx.fill(cx, cy, cx + dot, cy + dot, 0xFFFFFFFF);
        }
    }

    private void drawApp(DrawContext ctx, ChildManager.Status st) {
        // fundo do app
        ctx.fill(ax, ay, ax + aw, ay + ah, 0xFF000000);
        if (st == ChildManager.Status.RUNNING && texture != null) {
            ctx.drawTexture(textureId, dx, dy, dw, dh, 0f, 0f, texW, texH, texW, texH);
        } else {
            drawPlaceholder(ctx, st);
        }
    }

    private void drawPlaceholder(DrawContext ctx, ChildManager.Status st) {
        int cx = ax + aw / 2;
        int cy = ay + ah / 2;
        switch (st) {
            case OFF -> {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Minecraft desligado"), cx, cy - 10, 0xFFFFFF);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Clique para iniciar"), cx, cy + 4, 0xA0A0A0);
            }
            case STARTING -> {
                int dots = (int) ((System.currentTimeMillis() / 400) % 4);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Iniciando o Minecraft" + ".".repeat(dots)), cx, cy - 10, 0xFFFFFF);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Pode levar um minuto na primeira vez."), cx, cy + 4, 0xA0A0A0);
            }
            case DEAD -> {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("O Minecraft encerrou (veja tablet_inception/child.log)"), cx, cy - 10, 0xFF8080);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Clique para tentar novamente"), cx, cy + 4, 0xA0A0A0);
            }
            default -> { }
        }
        String err = ChildManager.lastError();
        if (!err.isEmpty()) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(err), Math.max(40, aw - 20));
            int y = cy + 22;
            for (OrderedText line : lines) {
                ctx.drawCenteredTextWithShadow(this.textRenderer, line, cx, y, 0xFF8080);
                y += 10;
            }
        }
    }

    private void updateCursorMode(ChildManager.Status st) {
        Channel ch = ChildManager.channel();
        boolean want = view == View.APP && captured && st == ChildManager.Status.RUNNING
                && ch != null && ch.cursorLocked();
        if (want != cursorDisabled) {
            cursorDisabled = want;
            lastValid = false;
            GLFW.glfwSetInputMode(this.client.getWindow().getHandle(), GLFW.GLFW_CURSOR,
                    want ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    // ------------------------------------------------------------------ navegacao

    private boolean inIconCell(double mx, double my) {
        return view == View.HOME && mx >= cellX && mx < cellX + cellW && my >= cellY && my < cellY + cellH;
    }

    private boolean insideApp(double mx, double my) {
        return mx >= ax && mx < ax + aw && my >= ay && my < ay + ah;
    }

    /** 0 = voltar, 1 = inicio, 2 = fechar app, -1 = fora da coluna de navegacao. */
    private int navAt(double mx, double my) {
        double px = (mx - ox) / s;
        double py = (my - oy) / s;
        if (px < NAV_X || px >= NAV_X + NAV_W || py < HOME_Y || py >= HOME_Y + HOME_H) return -1;
        if (py < 68) return 0;
        if (py < 108) return 1;
        return 2;
    }

    private void onNav(int nav) {
        switch (nav) {
            case 0 -> {
                if (view == View.APP) goHome();
                else this.close();
            }
            case 1 -> goHome();
            case 2 -> {
                uncapture();
                ChildManager.Status st = ChildManager.status();
                if (st == ChildManager.Status.STARTING || st == ChildManager.Status.RUNNING) {
                    ChildManager.stop();
                }
                view = View.HOME;
            }
            default -> { }
        }
    }

    private void goHome() {
        uncapture();
        view = View.HOME;
    }

    private void openApp() {
        view = View.APP;
        lastSeq = 0;
        ChildManager.Status st = ChildManager.status();
        if (st == ChildManager.Status.OFF || st == ChildManager.Status.DEAD) {
            ChildManager.start();
        }
    }

    // ------------------------------------------------------------------ entrada

    private boolean running() {
        return ChildManager.status() == ChildManager.Status.RUNNING && ChildManager.channel() != null;
    }

    private boolean forwarding() {
        return view == View.APP && captured && running();
    }

    private void sendAbsolute(double mx, double my) {
        Channel ch = ChildManager.channel();
        int ww = ch.windowWidth() > 0 ? ch.windowWidth() : 800;
        int wh = ch.windowHeight() > 0 ? ch.windowHeight() : 570;
        double x = Math.max(0, Math.min(ww - 1, (mx - dx) / dw * ww));
        double y = Math.max(0, Math.min(wh - 1, (my - dy) / dh * wh));
        ch.sendInput(Channel.IN_MOUSE_POS, 0, 0, 0, 0, x, y);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!forwarding()) {
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
        // Cursor preso dentro do jogo: tudo vai para o jogo.
        if (forwarding() && cursorDisabled) {
            ChildManager.channel().sendInput(Channel.IN_MOUSE_BUTTON, button, 1, 0, 0, 0, 0);
            pressedButtons.add(button);
            return true;
        }
        if (button == 0) {
            int nav = navAt(mx, my);
            if (nav >= 0) {
                onNav(nav);
                return true;
            }
            if (inIconCell(mx, my)) {
                openApp();
                return true;
            }
        }
        if (view == View.APP && insideApp(mx, my)) {
            ChildManager.Status st = ChildManager.status();
            if (st == ChildManager.Status.OFF || st == ChildManager.Status.DEAD) {
                ChildManager.start();
                return true;
            }
            if (running()) {
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
        return forwarding() || super.mouseDragged(mx, my, button, dX, dY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (forwarding()) {
            ChildManager.channel().sendInput(Channel.IN_MOUSE_SCROLL, 0, 0, 0, 0, 0, amount);
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (forwarding()) {
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
        if (forwarding()) {
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
