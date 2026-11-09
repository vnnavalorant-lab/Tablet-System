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
 * Apps: Minecraft (jogo interno), Camera, Galeria (ainda sem funcao) e ScuteAI (chat simples com respostas prontas).
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
    private static final Identifier ICON_SCUTE = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/scute_ai_icon.png");
    private static final Identifier ICON_CLOCK = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/clock_icon.png");
    private static final Identifier ICON_CRAFTING = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/crafting_icon.png");
    private static final Identifier ICON_NOTES = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/notes_icon.png");
    private static final Identifier ICON_BOOKS = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/books_icon.png");
    private static final Identifier ICON_BOLT = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/bolt_icon.png");
    private static final Identifier ICON_SETTINGS = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/settings_icon.png");
    private static final Identifier ICON_WEATHER = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/weather_icon.png");
    private static final Identifier SCUTE_SKIN = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/scute_skin.png");

    // Apps da tela inicial (na ordem em que aparecem, da esquerda para a direita)
    private static final int APP_MINECRAFT = 0;
    private static final int APP_CAMERA = 1;
    private static final int APP_GALLERY = 2;
    private static final int APP_SCUTE = 3;
    private static final Identifier[] APP_ICONS = {ICON, ICON_CAMERA, ICON_GALLERY, ICON_SCUTE,
            ICON_CLOCK, ICON_CRAFTING, ICON_NOTES, ICON_BOOKS, ICON_BOLT, ICON_SETTINGS, ICON_WEATHER};
    private static final String[] APP_NAMES = {"Minecraft", "Camera", "Galeria", "ScuteAI",
            "Rel\u00f3gio", "Crafting", "Notas", "Livros", "Raio", "Ajustes", "Clima"};
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

    // Chat do ScuteAI (pixels do painel)
    private static final int CHAT_X1 = SCREEN_X + 4;       // limite esquerdo do conteudo
    private static final int CHAT_X2 = NAV_X - 4;          // limite direito (antes da coluna de navegacao)
    private static final int CHAT_HEADER_Y = SCREEN_Y + STATUS_H;
    private static final int CHAT_HEADER_H = 12;
    private static final int CHAT_INPUT_H = 15;
    private static final int CHAT_TOP = CHAT_HEADER_Y + CHAT_HEADER_H;
    private static final int CHAT_BOTTOM = SCREEN_Y + SCREEN_H - CHAT_INPUT_H;
    private static final int HEAD = 11;                    // tamanho do rosto ao lado das mensagens
    private static final int BUBBLE_MAX_W = 140;
    private static final int COLOR_BG = 0xFF0A1250;
    private static final int COLOR_LINE = 0xFF4A5590;
    // Margens da janela (fracao) e espaco da dica de texto
    private static final double MARGIN_X = 0.04;
    private static final double MARGIN_TOP = 0.045;
    private static final double MARGIN_BOTTOM = 0.06;
    private static final int HINT_H = 14;

    private enum View { HOME, APP, SCUTE }

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
    private int cols = 1;       // colunas da grade de apps
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

    private double chatScroll;     // pixels do painel acima do fim da conversa
    private String chatInput = "";

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
        cols = Math.max(1, (CHAT_X2 - cellX + APP_GAP) / (cellW + APP_GAP));
    }

    private int appCellX(int i) {
        return cellX + (i % cols) * (cellW + APP_GAP);
    }

    private int appCellY(int i) {
        return cellY + (i / cols) * (cellH + APP_GAP);
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
        ScuteAi.tick();

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
        } else if (view == View.SCUTE) {
            drawScute(ctx);
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
                    : view == View.SCUTE
                    ? "Digite e aperte Enter  |  Roda do mouse: rolar  |  Esc: voltar"
                    : "Toque em um app para abrir  |  Esc: fechar o tablet";
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
            int x = appCellX(i);
            int cellY = appCellY(i);

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

    // ------------------------------------------------------------------ ScuteAI

    private double chatK() {
        return Math.max(1, Math.round(realS * 0.6f)) / (double) realS;
    }

    /** Rosto (8x8 + camada do chapeu) de uma skin 64x64. */
    private void drawHead(DrawContext ctx, Identifier skin, int x, int y, int size) {
        ctx.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
        ctx.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
    }

    private Identifier playerSkin() {
        if (this.client.player != null) return this.client.player.getSkinTexture();
        return SCUTE_SKIN;
    }

    private String playerName() {
        return this.client.player != null ? this.client.player.getName().getString() : "jogador";
    }

    /** Retangulo com cantos "cortados" (estilo pixel art), com borda de 1 pixel. */
    private void drawBubbleBox(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        ctx.fill(x + 1, y, x + w - 1, y + h, border);
        ctx.fill(x, y + 1, x + w, y + h - 1, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void drawScute(DrawContext ctx) {
        final double k = chatK();
        final double lineH = 9 * k;
        final int padX = 3;
        final double padY = 2;
        final int viewH = CHAT_BOTTOM - CHAT_TOP;

        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);

        // ---- monta a lista (mensagens + indicador "digitando")
        List<ScuteAi.Msg> msgs = new java.util.ArrayList<>(ScuteAi.messages());
        boolean typing = ScuteAi.isTyping();
        if (typing) msgs.add(new ScuteAi.Msg(false, "..."));

        int n = msgs.size();
        List<List<OrderedText>> wrapped = new java.util.ArrayList<>(n);
        int[] bw = new int[n];
        int[] bh = new int[n];
        int[] rowH = new int[n];
        final int gap = 5;
        double total = 0;
        for (int i = 0; i < n; i++) {
            int maxText = (int) ((BUBBLE_MAX_W - 2 * padX) / k);
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(msgs.get(i).text()), maxText);
            wrapped.add(lines);
            int w = 0;
            for (OrderedText l : lines) w = Math.max(w, this.textRenderer.getWidth(l));
            bw[i] = (int) Math.ceil(w * k) + 2 * padX;
            bh[i] = (int) Math.ceil(lines.size() * lineH + 2 * padY);
            rowH[i] = Math.max(bh[i], HEAD);
            total += rowH[i] + gap;
        }
        double maxScroll = Math.max(0, total + gap - viewH);
        chatScroll = Math.max(0, Math.min(maxScroll, chatScroll));

        // ---- desenha de baixo para cima
        double y = CHAT_BOTTOM - gap + chatScroll;
        for (int i = n - 1; i >= 0; i--) {
            int rh = rowH[i];
            int top = (int) Math.round(y - rh);
            if (top + rh < CHAT_TOP) break;           // acima da area visivel
            if (top <= CHAT_BOTTOM) {
                ScuteAi.Msg m = msgs.get(i);
                int by = top + (rh - bh[i]) / 2;
                int hy = top + (rh - HEAD) / 2;
                int bx;
                int fill;
                int border;
                if (m.fromPlayer()) {
                    drawHead(ctx, playerSkin(), CHAT_X2 - HEAD, hy, HEAD);
                    bx = CHAT_X2 - HEAD - 3 - bw[i];
                    fill = 0xFF6B3E6E;
                    border = 0xFF9A6FA0;
                } else {
                    drawHead(ctx, SCUTE_SKIN, CHAT_X1, hy, HEAD);
                    bx = CHAT_X1 + HEAD + 3;
                    fill = 0xFF3B4A78;
                    border = 0xFF8FA0CC;
                }
                drawBubbleBox(ctx, bx, by, bw[i], bh[i], fill, border);
                if (typing && i == n - 1) {
                    int dots = 1 + (int) ((System.currentTimeMillis() / 300) % 3);
                    drawText(ctx, Text.literal(".".repeat(dots)).asOrderedText(),
                            bx + padX, by + padY + k, k, 0xFFFFFFFF);
                } else {
                    double ty = by + padY + k / 2.0;
                    for (OrderedText l : wrapped.get(i)) {
                        drawText(ctx, l, bx + padX, ty, k, 0xFFFFFFFF);
                        ty += lineH;
                    }
                }
            }
            y -= rh + gap;
        }

        // ---- cabecalho (cobre mensagens que passam do topo)
        ctx.fill(SCREEN_X, CHAT_HEADER_Y, SCREEN_X + SCREEN_W, CHAT_TOP, COLOR_BG);
        double hk = k * 1.15;
        drawTextCentered(ctx, Text.literal(ScuteAi.NAME).asOrderedText(),
                (CHAT_X1 + CHAT_X2) / 2.0, CHAT_HEADER_Y + (CHAT_HEADER_H - 8 * hk) / 2.0 - 0.5, hk, 0xFFFFFFFF);
        ctx.fill(CHAT_X1, CHAT_TOP - 1, CHAT_X2, CHAT_TOP, COLOR_LINE);

        // ---- barra de digitacao (cobre mensagens que passam da base)
        ctx.fill(SCREEN_X, CHAT_BOTTOM, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);
        int ix1 = CHAT_X1;
        int ix2 = CHAT_X2;
        int iy1 = CHAT_BOTTOM + 2;
        int iy2 = SCREEN_Y + SCREEN_H - 3;
        drawBubbleBox(ctx, ix1, iy1, ix2 - ix1, iy2 - iy1, 0xFF05082A, COLOR_LINE);

        double ik = k;
        double ty = iy1 + (iy2 - iy1 - 8 * ik) / 2.0 + ik / 2.0;
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (chatInput.isEmpty()) {
            drawText(ctx, Text.literal("Digite uma mensagem...").asOrderedText(), ix1 + 4, ty, ik, 0xFF707AA8);
            if (blink) drawText(ctx, Text.literal("_").asOrderedText(), ix1 + 4, ty, ik, 0xFFFFFFFF);
        } else {
            String shown = chatInput;
            double maxW = (ix2 - ix1) - 12;
            while (shown.length() > 1 && this.textRenderer.getWidth(shown + "_") * ik > maxW) {
                shown = shown.substring(1);
            }
            drawText(ctx, Text.literal(shown + (blink ? "_" : "")).asOrderedText(), ix1 + 4, ty, ik, 0xFFFFFFFF);
        }

        // ---- moldura, barra de status e navegacao por cima
        ctx.drawTexture(BORDER, 0, 0, PANEL_W, PANEL_H, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    private void sendChat() {
        if (chatInput.trim().isEmpty()) return;
        ScuteAi.send(chatInput, playerName());
        chatInput = "";
        chatScroll = 0;
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
        for (int i = 0; i < APP_ICONS.length; i++) {
            int x = appCellX(i);
            int y = appCellY(i);
            if (px >= x && px < x + cellW && py >= y && py < y + cellH) return i;
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
                if (view != View.HOME) goHome();
                else this.close();
            }
            case 1 -> goHome();
            case 2 -> {
                if (view == View.SCUTE) {
                    goHome();       // o ScuteAI nao tem processo para encerrar
                    return;
                }
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
            if (app == APP_SCUTE) {
                view = View.SCUTE;
                chatScroll = 0;
                return true;
            }
            if (app >= 0) {
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
        if (view == View.SCUTE) {
            chatScroll += amount * 12;   // clampado no proximo frame
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
        if (view == View.SCUTE) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> goHome();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> sendChat();
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!chatInput.isEmpty()) chatInput = chatInput.substring(0, chatInput.length() - 1);
                }
                case GLFW.GLFW_KEY_PAGE_UP -> chatScroll += 40;
                case GLFW.GLFW_KEY_PAGE_DOWN -> chatScroll -= 40;
                case GLFW.GLFW_KEY_V -> {
                    if (Screen.hasControlDown()) {
                        String clip = this.client.keyboard.getClipboard();
                        if (clip != null) {
                            for (char c : clip.toCharArray()) typeChar(c);
                        }
                    }
                }
                default -> { }
            }
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
        if (view == View.SCUTE) {
            typeChar(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void typeChar(char c) {
        if (net.minecraft.SharedConstants.isValidChar(c) && chatInput.length() < ScuteAi.MAX_INPUT) {
            chatInput += c;
        }
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
