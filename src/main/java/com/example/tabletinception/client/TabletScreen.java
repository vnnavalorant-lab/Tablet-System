package com.example.tabletinception.client;

import com.example.tabletinception.TabletConfig;
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
 * Tablet: tela inicial com os icones do Minecraft, Camera e Galeria; ao tocar no icone, abre o Minecraft interno.
 *
 * O tablet e desenhado em "pixels do painel" (as texturas tem 284x166) com escala inteira em pixels reais
 * (imagem nitida) e ocupa quase toda a janela.
 *  - tablet_panel.png : painel completo (tela inicial).
 *  - tablet_border.png: moldura + barra de status/navegacao translucidas, desenhada POR CIMA do jogo.
 *
 * Navegacao (coluna da direita):  triangulo = voltar | circulo = inicio | quadrado = fechar o app.
 * No app: clique na tela captura mouse/teclado, Ctrl direito solta, Esc (solto) fecha o tablet.
 */
public class TabletScreen extends Screen {
    private static final Identifier PANEL = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/tablet_panel.png");
    private static final Identifier BORDER = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/tablet_border.png");
    private static final Identifier ICON = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/minecraft_icon.png");
    private static final Identifier ICON_CAMERA = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/camera_icon.png");
    private static final Identifier ICON_GALLERY = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/gallery_icon.png");

    // Apps da tela inicial (na ordem em que aparecem, da esquerda para a direita)
    private static final int APP_MINECRAFT = 0;
    private static final int APP_CAMERA = 1;
    private static final int APP_GALLERY = 2;
    private static final Identifier[] APP_ICONS = {ICON, ICON_CAMERA, ICON_GALLERY};
    private static final String[] APP_NAMES = {"Minecraft", "Camera", "Galeria"};
    private static final int APP_GAP = 4;        // espaco entre as celulas dos apps
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    // Medidas das texturas (pixels do painel)
    private static final int PANEL_W = 284;
    private static final int PANEL_H = 166;
    private static final int SCREEN_X = 8;       // area total da tela (o jogo ocupa toda ela)
    private static final int SCREEN_Y = 8;
    private static final int SCREEN_W = 269;
    private static final int SCREEN_H = 150;
    private static final int STATUS_H = 9;       // barra de status (topo da tela)
    private static final int HOME_X = 8;         // area de apps da tela inicial (abaixo da barra, sem a navegacao)
    private static final int HOME_Y = 17;
    private static final int NAV_X = 263;        // coluna de navegacao
    private static final int NAV_W = 14;
    private static final int NAV_BACK_END = 66;  // limites verticais dos tres botoes
    private static final int NAV_HOME_END = 107;
    private static final int ICON_SIZE = 20;

    // Margens da janela (fracao) e espaco da dica de texto
    private static final double MARGIN_X = 0.04;
    private static final double MARGIN_TOP = 0.045;
    private static final double MARGIN_BOTTOM = 0.06;
    private static final int HINT_H = 14;

    private enum View { HOME, APP }

    private View view = View.HOME;

    // layout
    private double gs = 1;       // fator de escala da GUI
    private int realS = 1;       // pixels reais por pixel do painel
    private double sf = 1;       // unidades da GUI por pixel do painel
    private double ox;
    private double oy;
    private int dx;              // retangulo do frame do jogo (pixels do painel)
    private int dy;
    private int dw;
    private int dh;
    private int cellX;       // x da primeira celula; as demais seguem a direita (cellW + APP_GAP)
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

    private double panelX(double mx) { return (mx - ox) / sf; }

    private double panelY(double my) { return (my - oy) / sf; }

    /** Tamanho da fonte (em pixels reais por pixel da fonte) -> fator de escala no espaco do painel. */
    private double fontK(int realPixelsPerFontPixel) { return realPixelsPerFontPixel / (double) realS; }

    private int labelN() { return Math.max(1, Math.round(realS * 0.55f)); }

    private int statusN() { return Math.max(1, Math.min(Math.round(realS * 0.75f), (STATUS_H * realS) / 8)); }

    private int bodyN() { return Math.max(1, Math.round(realS * 0.4f)); }

    private void computeLayout(ChildManager.Status st) {
        Window win = this.client.getWindow();
        gs = win.getScaleFactor();

        double top = this.height * MARGIN_TOP;
        double bottom = this.height * MARGIN_BOTTOM + HINT_H;
        double availWr = (this.width - 2 * this.width * MARGIN_X) * gs;
        double availHr = (this.height - top - bottom) * gs;

        realS = (int) Math.max(1, Math.floor(Math.min(availHr / PANEL_H, availWr / PANEL_W)));
        sf = realS / gs;

        double x0 = Math.round((this.width * gs - PANEL_W * (double) realS) / 2.0);
        double y0 = Math.round(top * gs + (availHr - PANEL_H * (double) realS) / 2.0);
        ox = x0 / gs;
        oy = y0 / gs;

        // O frame do jogo ocupa a tela inteira (por baixo da moldura translucida).
        if (view == View.APP && st == ChildManager.Status.RUNNING && texW > 0) {
            double aspect = texW / (double) texH;
            if (SCREEN_W / aspect > SCREEN_H) {
                dh = SCREEN_H;
                dw = Math.max(1, (int) Math.round(dh * aspect));
            } else {
                dw = SCREEN_W;
                dh = Math.max(1, (int) Math.round(dw / aspect));
            }
            dx = SCREEN_X + (SCREEN_W - dw) / 2;
            dy = SCREEN_Y + (SCREEN_H - dh) / 2;
        } else {
            dx = SCREEN_X;
            dy = SCREEN_Y;
            dw = SCREEN_W;
            dh = SCREEN_H;
        }

        double kl = fontK(labelN());
        int labelW = 0;
        for (String name : APP_NAMES) {
            labelW = Math.max(labelW, (int) Math.ceil(this.textRenderer.getWidth(name) * kl));
        }
        cellW = Math.max(ICON_SIZE + 8, labelW + 6);
        cellH = 2 + ICON_SIZE + 2 + (int) Math.ceil(8 * kl) + 3;
        cellX = HOME_X + 8;
        cellY = HOME_Y + 8;
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

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(ox, oy, 0);
        m.scale((float) sf, (float) sf, 1f);

        if (view == View.HOME) {
            ctx.drawTexture(PANEL, 0, 0, PANEL_W, PANEL_H, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
            drawHome(ctx, st, panelX(mouseX), panelY(mouseY));
        } else {
            drawApp(ctx, st);
        }
        drawStatusBar(ctx);

        m.pop();

        // Dica abaixo do tablet
        int hintY = (int) (oy + PANEL_H * sf + 6);
        if (hintY + 9 <= this.height) {
            String hint = view == View.APP
                    ? (captured ? "Ctrl direito: soltar mouse e teclado"
                                : "Clique na tela para controlar  |  Esc: fechar o tablet")
                    : "Toque no icone do Minecraft para abrir  |  Esc: fechar o tablet";
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, hintY, 0xA0A0A0);
        }

        super.render(ctx, mouseX, mouseY, delta);
        updateCursorMode(st);
    }

    private void drawText(DrawContext ctx, OrderedText text, double x, double y, double k, int color) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale((float) k, (float) k, 1f);
        ctx.drawTextWithShadow(this.textRenderer, text, 0, 0, color);
        m.pop();
    }

    private void drawTextCentered(DrawContext ctx, OrderedText text, double cx, double y, double k, int color) {
        double w = this.textRenderer.getWidth(text) * k;
        drawText(ctx, text, cx - w / 2.0, y, k, color);
    }

    private void drawStatusBar(DrawContext ctx) {
        double k = fontK(statusN());
        double y = SCREEN_Y + (STATUS_H - 8 * k) / 2.0 + k / 2.0;
        drawText(ctx, Text.literal(LocalTime.now().format(CLOCK)).asOrderedText(),
                SCREEN_X + 4, y, k, 0xFFFFFFFF);
    }

    private void drawHome(DrawContext ctx, ChildManager.Status st, double pmx, double pmy) {
        double kl = fontK(labelN());
        for (int i = 0; i < APP_ICONS.length; i++) {
            int x = cellX + i * (cellW + APP_GAP);

            if (pmx >= x && pmx < x + cellW && pmy >= cellY && pmy < cellY + cellH) {
                ctx.fill(x, cellY, x + cellW, cellY + cellH, 0x33FFFFFF);
            }

            int iconX = x + (cellW - ICON_SIZE) / 2;
            ctx.drawTexture(APP_ICONS[i], iconX, cellY + 2, ICON_SIZE, ICON_SIZE, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            drawTextCentered(ctx, Text.literal(APP_NAMES[i]).asOrderedText(),
                    x + cellW / 2.0, cellY + 2 + ICON_SIZE + 2, kl, 0xFFFFFFFF);

            // Indicador de app em execucao (so o Minecraft roda de verdade por enquanto)
            if (i == APP_MINECRAFT && (st == ChildManager.Status.STARTING || st == ChildManager.Status.RUNNING)) {
                int cx = x + cellW / 2 - 1;
                int cy = cellY + cellH - 3;
                ctx.fill(cx, cy, cx + 2, cy + 2, 0xFFFFFFFF);
            }
        }
    }

    private void drawApp(DrawContext ctx, ChildManager.Status st) {
        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, 0xFF000000);
        if (st == ChildManager.Status.RUNNING && texture != null) {
            ctx.drawTexture(textureId, dx, dy, dw, dh, 0f, 0f, texW, texH, texW, texH);
        } else {
            drawPlaceholder(ctx, st);
        }
        // Moldura, barra de status e navegacao por cima do jogo
        ctx.drawTexture(BORDER, 0, 0, PANEL_W, PANEL_H, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    private void drawPlaceholder(DrawContext ctx, ChildManager.Status st) {
        double k = fontK(bodyN());
        double cx = SCREEN_X + SCREEN_W / 2.0;
        double cy = SCREEN_Y + SCREEN_H / 2.0;
        double line = 10 * k;
        String l1;
        String l2;
        int c1 = 0xFFFFFF;
        switch (st) {
            case OFF -> { l1 = "Minecraft desligado"; l2 = "Clique para iniciar"; }
            case STARTING -> {
                int dots = (int) ((System.currentTimeMillis() / 400) % 4);
                l1 = "Iniciando o Minecraft" + ".".repeat(dots);
                l2 = "Pode levar um minuto na primeira vez.";
            }
            case DEAD -> {
                l1 = "O Minecraft encerrou (veja tablet_inception/child.log)";
                l2 = "Clique para tentar novamente";
                c1 = 0xFF8080;
            }
            default -> { l1 = ""; l2 = ""; }
        }
        drawTextCentered(ctx, Text.literal(l1).asOrderedText(), cx, cy - line, k, c1);
        drawTextCentered(ctx, Text.literal(l2).asOrderedText(), cx, cy + 2 * k, k, 0xA0A0A0);

        String err = ChildManager.lastError();
        if (!err.isEmpty()) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(err), Math.max(40, (int) (SCREEN_W / k) - 20));
            double y = cy + line * 2;
            for (OrderedText l : lines) {
                drawTextCentered(ctx, l, cx, y, k, 0xFF8080);
                y += line;
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

    /** Indice do app sob o mouse na tela inicial, ou -1. */
    private int appAt(double mx, double my) {
        if (view != View.HOME) return -1;
        double px = panelX(mx);
        double py = panelY(my);
        if (py < cellY || py >= cellY + cellH) return -1;
        for (int i = 0; i < APP_ICONS.length; i++) {
            int x = cellX + i * (cellW + APP_GAP);
            if (px >= x && px < x + cellW) return i;
        }
        return -1;
    }

    private boolean insideApp(double mx, double my) {
        double px = panelX(mx);
        double py = panelY(my);
        return px >= SCREEN_X && px < SCREEN_X + SCREEN_W && py >= SCREEN_Y && py < SCREEN_Y + SCREEN_H;
    }

    /** 0 = voltar, 1 = inicio, 2 = fechar app, -1 = fora da coluna de navegacao. */
    private int navAt(double mx, double my) {
        double px = panelX(mx);
        double py = panelY(my);
        if (px < NAV_X || px >= NAV_X + NAV_W || py < SCREEN_Y || py >= SCREEN_Y + SCREEN_H) return -1;
        if (py < NAV_BACK_END) return 0;
        if (py < NAV_HOME_END) return 1;
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

    /** Inicia o Minecraft interno com a mesma proporcao da area do app. */
    private void startChild() {
        TabletConfig cfg = TabletConfig.load();
        int h = cfg.height;
        int w = cfg.width > 0 ? cfg.width : (int) Math.round(h * (SCREEN_W / (double) SCREEN_H));
        w = Math.max(320, Math.min(1920, w));
        ChildManager.start(w, h);
    }

    private void openApp() {
        view = View.APP;
        lastSeq = 0;
        ChildManager.Status st = ChildManager.status();
        if (st == ChildManager.Status.OFF || st == ChildManager.Status.DEAD) {
            startChild();
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
        int ww = ch.windowWidth() > 0 ? ch.windowWidth() : 854;
        int wh = ch.windowHeight() > 0 ? ch.windowHeight() : 480;
        double x = Math.max(0, Math.min(ww - 1, (panelX(mx) - dx) / dw * ww));
        double y = Math.max(0, Math.min(wh - 1, (panelY(my) - dy) / dh * wh));
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
            int app = appAt(mx, my);
            if (app == APP_MINECRAFT) {
                openApp();
                return true;
            }
            if (app == APP_CAMERA || app == APP_GALLERY) {
                return true; // ainda sem funcao
            }
        }
        if (view == View.APP && insideApp(mx, my)) {
            ChildManager.Status st = ChildManager.status();
            if (st == ChildManager.Status.OFF || st == ChildManager.Status.DEAD) {
                startChild();
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
