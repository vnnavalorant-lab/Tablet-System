package com.example.tabletinception.client;

import com.example.tabletinception.TabletConfig;
import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.channel.Channel;
import com.example.tabletinception.client.ipad.TabletClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
 * Apps: Minecraft (jogo interno), ScuteAI (chat simples com respostas prontas), Ajustes (papel de parede e cores),
 * Clima (le o clima real do jogo), Notas (bloco de notas salvo em arquivo), Relogio (so a hora atual) e
 * Musica (lista de musicas do Minecraft). Camera, Galeria, Livros e App Store mostram "Aplicativo em Desenvolvimento".
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
    private static final Identifier ICON_NOTES = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/notes_icon.png");
    private static final Identifier ICON_BOOKS = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/books_icon.png");
    private static final Identifier ICON_SETTINGS = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/settings_icon.png");
    private static final Identifier ICON_WEATHER = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/weather_icon.png");
    private static final Identifier ICON_MUSIC = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/music_icon.png");
    private static final Identifier ICON_STORE = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/app_store_icon.png");
    private static final Identifier SCUTE_SKIN = new Identifier(TabletInceptionMod.MOD_ID, "textures/gui/scute_skin.png");

    // Apps da tela inicial (na ordem em que aparecem, da esquerda para a direita)
    private static final int APP_MINECRAFT = 0;
    private static final int APP_CAMERA = 1;
    private static final int APP_GALLERY = 2;
    private static final int APP_SCUTE = 3;
    private static final int APP_CLOCK = 4;
    private static final int APP_NOTES = 6;
    private static final int APP_BOOKS = 7;
    private static final int APP_MUSIC = 5;     // ocupa o lugar do antigo Crafting
    private static final int APP_STORE = 10;
    private static final int APP_SETTINGS = 8;   // posicao de "Ajustes" em APP_ICONS/APP_NAMES
    private static final int APP_WEATHER = 9;    // posicao de "Clima" em APP_ICONS/APP_NAMES
    private static final Identifier[] APP_ICONS = {ICON, ICON_CAMERA, ICON_GALLERY, ICON_SCUTE,
            ICON_CLOCK, ICON_MUSIC, ICON_NOTES, ICON_BOOKS, ICON_SETTINGS, ICON_WEATHER,
            ICON_STORE};
    private static final String[] APP_NAMES = {"app.minecraft", "app.camera", "app.gallery", "app.scute",
            "app.clock", "app.music", "app.notes", "app.books", "app.settings", "app.weather",
            "app.store"};   // chaves de traducao (use appName(i))

    private static String appName(int i) {
        return Lang.t(APP_NAMES[i]);
    }
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
    private static final int COLOR_BG = 0xFF121214;
    private static final int COLOR_LINE = 0xFF3C3C46;
    // Notas / Musica (pixels do painel)
    private static final int NOTES_X1 = CHAT_X1;
    private static final int NOTES_X2 = CHAT_X2;
    private static final int NOTES_Y1 = CHAT_TOP + 3;
    private static final int NOTES_Y2 = SCREEN_Y + SCREEN_H - 4;
    private static final int MUS_TOP = CHAT_TOP + 2;
    private static final int MUS_BOTTOM = CHAT_BOTTOM;
    private static final int MUS_ROW_H = 10;
    private static final int MUS_STOP_W = 40;
    private static final int MUS_STOP_H = 10;
    private static final int MUS_STOP_X = CHAT_X2 - MUS_STOP_W;
    private static final int MUS_STOP_Y = CHAT_BOTTOM + 2;
    // Margens da janela (fracao) e espaco da dica de texto
    private static final double MARGIN_X = 0.04;
    private static final double MARGIN_TOP = 0.045;
    private static final double MARGIN_BOTTOM = 0.06;
    private static final int HINT_H = 14;

    private enum View { HOME, APP, SCUTE, SETTINGS, WEATHER, NOTES, CLOCK, MUSIC, DEV }

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
    private int weatherScroll;     // linhas de comentario do Clima roladas
    private int notesScroll;       // linhas das Notas roladas para cima (0 = fim do texto)
    private int musicScroll;       // primeira musica visivel na lista
    private String devTitle = "";  // nome do app "em desenvolvimento" aberto

    // Ajustes (pixels do painel)
    private static final int PREV_X = CHAT_X1 + 4;
    private static final int PREV_Y = CHAT_TOP + 18;
    private static final int PREV_W = 90;
    private static final int PREV_H = 50;
    private static final int BTN_X = PREV_X + PREV_W + 8;
    private static final int BTN_W = CHAT_X2 - BTN_X - 4;
    private static final int BTN_H = 14;
    private static final int BTN_PICK_Y = PREV_Y + 4;
    private static final int BTN_RESET_Y = BTN_PICK_Y + BTN_H + 5;
    private static final int TAB_Y = CHAT_TOP + 2;
    private static final int TAB_H = 11;
    private static final int SW_W = 16;
    private static final int SW_H = 14;
    private static final int SW_STEP = 20;
    private static final int SW_X = CHAT_X1 + 4;
    private static final int SW_TOP_Y = CHAT_TOP + 28;
    private static final int SW_SIDE_Y = SW_TOP_Y + 40;
    private static final String[] TAB_NAMES = {"tab.wallpaper", "tab.colors"};   // chaves de traducao

    private static String tabName(int i) {
        return Lang.t(TAB_NAMES[i]);
    }
    private int settingsTab;
    private volatile Path pendingPick;
    private volatile boolean picking;
    private String settingsMsg = "";
    private int settingsMsgColor = 0xFF9A9AA8;
    private String chatInput = "";

    private boolean captured;
    private boolean cursorDisabled;
    private boolean lastValid;
    private double lastMx;
    private double lastMy;
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> pressedButtons = new HashSet<>();

    public TabletScreen() {
        super(Text.translatable("screen.tablet_inception.tablet"));
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
        for (int ai = 0; ai < APP_NAMES.length; ai++) {
            labelW = Math.max(labelW, (int) Math.ceil(this.textRenderer.getWidth(appName(ai)) * kl));
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
        Wallpaper.ensureLoaded();
        Theme.ensureLoaded();
        applyPendingWallpaper();
        Notes.ensureLoaded();
        Notes.tick();

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
            if (Wallpaper.has()) {
                drawWallpaper(ctx, SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H);
            }
            if (Wallpaper.has() || Theme.any()) {
                // moldura, barra de status e navegacao (com as cores escolhidas) por cima
                drawBars(ctx);
            }
            drawHome(ctx, st, panelX(mouseX), panelY(mouseY));
        } else if (view == View.SCUTE) {
            drawScute(ctx);
        } else if (view == View.SETTINGS) {
            drawSettings(ctx, panelX(mouseX), panelY(mouseY));
        } else if (view == View.WEATHER) {
            drawWeather(ctx);
        } else if (view == View.NOTES) {
            drawNotes(ctx);
        } else if (view == View.CLOCK) {
            drawClock(ctx);
        } else if (view == View.MUSIC) {
            drawMusic(ctx, panelX(mouseX), panelY(mouseY));
        } else if (view == View.DEV) {
            drawDev(ctx);
        } else {
            drawApp(ctx, st);
        }
        drawStatusBar(ctx);

        m.pop();

        // Dica abaixo do tablet
        int hintY = (int) (oy + PANEL_H * sf + 6);
        if (hintY + 9 <= this.height) {
            String hint = view == View.APP
                    ? (captured ? Lang.t("hint.release")
                                : Lang.t("hint.app"))
                    : view == View.SCUTE
                    ? Lang.t("hint.scute")
                    : view == View.SETTINGS
                    ? Lang.t("hint.settings")
                    : view == View.WEATHER
                    ? Lang.t("hint.weather")
                    : view == View.NOTES
                    ? Lang.t("hint.notes")
                    : view == View.MUSIC
                    ? Lang.t("hint.music")
                    : view == View.CLOCK || view == View.DEV
                    ? Lang.t("hint.back")
                    : Lang.t("hint.home");
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
        // com a barra branca o horario fica escuro para continuar legivel
        drawText(ctx, Text.literal(LocalTime.now().format(CLOCK)).asOrderedText(),
                SCREEN_X + 4, y, k, Theme.isWhite(Theme.top()) ? 0xFF1A1A2E : 0xFFFFFFFF);
        // clima atual do jogo (no centro da barra), colorido pela nota: verde = bom, vermelho = ruim
        if (view != View.APP && view != View.WEATHER && this.client.world != null) {
            WeatherInfo w = WeatherInfo.current(this.client);
            if (!w.shortLabel.isEmpty()) {
                drawTextCentered(ctx, Text.literal(w.shortLabel).asOrderedText(),
                        SCREEN_X + SCREEN_W / 2.0, y, k, w.rating.color);
            }
        }
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

            // O icone do Clima mostra a nota do tempo agora (verde = bom, amarelo = regular, vermelho = ruim)
            if (i == APP_WEATHER && this.client.world != null) {
                int dotX = iconX + ICON_SIZE - 5;
                int dotY = cellY + 1;
                ctx.fill(dotX - 1, dotY - 1, dotX + 5, dotY + 5, 0xFF121214);
                ctx.fill(dotX, dotY, dotX + 4, dotY + 4, WeatherInfo.current(this.client).rating.color);
            }

            drawTextCentered(ctx, Text.literal(appName(i)).asOrderedText(),
                    x + cellW / 2.0, cellY + 2 + ICON_SIZE + 2, kl, 0xFFFFFFFF);

            // Indicador de app em execucao (so o Minecraft roda de verdade por enquanto)
            if (i == APP_MINECRAFT && (st == ChildManager.Status.STARTING || st == ChildManager.Status.RUNNING)) {
                int cx = x + cellW / 2 - 1;
                int cy = cellY + cellH - 3;
                ctx.fill(cx, cy, cx + 2, cy + 2, 0xFFFFFFFF);
            }
        }
    }

    // ------------------------------------------------------------------ Ajustes / papel de parede

    /** Desenha o papel de parede cobrindo o retangulo (corta o excesso, sem distorcer). */
    private void drawWallpaper(DrawContext ctx, int x, int y, int w, int h) {
        int iw = Wallpaper.width();
        int ih = Wallpaper.height();
        double areaAspect = w / (double) h;
        int rw;
        int rh;
        if (iw / (double) ih > areaAspect) {
            rh = ih;
            rw = Math.max(1, (int) Math.round(ih * areaAspect));
        } else {
            rw = iw;
            rh = Math.max(1, (int) Math.round(iw / areaAspect));
        }
        float u = (iw - rw) / 2f;
        float v = (ih - rh) / 2f;
        ctx.drawTexture(Wallpaper.id(), x, y, w, h, u, v, rw, rh, iw, ih);
    }

    private boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean hover, boolean enabled) {
        int fill = !enabled ? 0xFF202024 : hover ? 0xFF4A4A56 : 0xFF32323A;
        drawBubbleBox(ctx, x, y, w, h, fill, enabled ? 0xFF7A7A88 : COLOR_LINE);
        double k = chatK();
        drawTextCentered(ctx, Text.literal(label).asOrderedText(), x + w / 2.0,
                y + (h - 8 * k) / 2.0 + k / 2.0, k, enabled ? 0xFFFFFFFF : 0xFF6C6C78);
    }

    private static final int DEFAULT_TOP = 0x0C0C0F;    // cores padrao das barras
    private static final int DEFAULT_SIDE = 0x1E1E24;

    /**
     * Desenha o painel superior (barra de status) e o painel lateral (navegacao) na cor escolhida em
     * Ajustes > Cores (ou na cor padrao) e, por cima, a moldura do tablet com os icones.
     * Sobre o jogo interno e sobre o papel de parede da tela inicial as barras ficam translucidas.
     */
    private void drawBars(DrawContext ctx) {
        boolean glass = view == View.APP || (view == View.HOME && Wallpaper.has());
        boolean customTop = Theme.top() != 0;
        boolean customSide = Theme.side() != 0;
        int top = customTop ? Theme.top() : DEFAULT_TOP;
        int side = customSide ? Theme.side() : DEFAULT_SIDE;
        int topA = glass ? (customTop ? 0xD0 : 0x50) : 0xFF;
        int sideA = glass ? (customSide ? 0xB0 : 0x19) : 0xFF;
        // branco: translucido de proposito (80% no painel superior, 50% no lateral)
        if (Theme.isWhite(Theme.top())) topA = 0xCC;
        if (Theme.isWhite(Theme.side())) sideA = 0x80;
        ctx.fill(NAV_X, SCREEN_Y + STATUS_H, NAV_X + NAV_W, SCREEN_Y + SCREEN_H, (sideA << 24) | side);
        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + STATUS_H, (topA << 24) | top);
        ctx.drawTexture(BORDER, 0, 0, PANEL_W, PANEL_H, 0f, 0f, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    private int tabX(int i) {
        double k = chatK();
        int x = CHAT_X1 + 4;
        for (int j = 0; j < i; j++) {
            x += (int) Math.ceil(this.textRenderer.getWidth(tabName(j)) * k) + 10 + 3;
        }
        return x;
    }

    private int tabW(int i) {
        return (int) Math.ceil(this.textRenderer.getWidth(tabName(i)) * chatK()) + 10;
    }

    private void drawSettingsTabs(DrawContext ctx, double pmx, double pmy) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean sel = i == settingsTab;
            int x = tabX(i);
            int w = tabW(i);
            boolean hover = inRect(pmx, pmy, x, TAB_Y, w, TAB_H);
            drawBubbleBox(ctx, x, TAB_Y, w, TAB_H, sel ? 0xFF4A4A56 : hover ? 0xFF32323A : 0xFF1C1C22,
                    sel ? 0xFFFFFFFF : COLOR_LINE);
            double k = chatK();
            drawTextCentered(ctx, Text.literal(tabName(i)).asOrderedText(), x + w / 2.0,
                    TAB_Y + (TAB_H - 8 * k) / 2.0 + k / 2.0, k, 0xFFFFFFFF);
        }
    }

    private void drawSwatchRow(DrawContext ctx, String label, int y, int current, double pmx, double pmy) {
        double k = chatK();
        drawText(ctx, Text.literal(label).asOrderedText(), SW_X, y - 11 * k, k, 0xFFFFFFFF);
        for (int i = 0; i < Theme.PALETTE.length; i++) {
            int x = SW_X + i * SW_STEP;
            int rgb = Theme.PALETTE[i];
            boolean selected = rgb == current;
            boolean hover = inRect(pmx, pmy, x, y, SW_W, SW_H);
            ctx.fill(x - 1, y - 1, x + SW_W + 1, y + SW_H + 1,
                    selected ? 0xFFFFFFFF : hover ? 0xFF9A9AA8 : COLOR_LINE);
            if (rgb == 0) {
                ctx.fill(x, y, x + SW_W, y + SW_H, 0xFF1E1E24);      // cor padrao do tablet
                drawTextCentered(ctx, Text.literal("x").asOrderedText(), x + SW_W / 2.0,
                        y + (SW_H - 8 * k) / 2.0 + k / 2.0, k, 0xFF9A9AA8);
            } else {
                ctx.fill(x, y, x + SW_W, y + SW_H, 0xFF000000 | rgb);
            }
        }
    }

    private void drawColorsTab(DrawContext ctx, double pmx, double pmy) {
        double k = chatK();
        drawSwatchRow(ctx, Lang.t("settings.top_panel"), SW_TOP_Y, Theme.top(), pmx, pmy);
        drawSwatchRow(ctx, Lang.t("settings.side_panel"), SW_SIDE_Y, Theme.side(), pmx, pmy);
        int textW = (int) ((CHAT_X2 - SW_X) / k);
        double ty = SW_SIDE_Y + SW_H + 8;
        for (OrderedText l : this.textRenderer.wrapLines(
                Text.literal(Lang.t("settings.color_help")), textW)) {
            drawText(ctx, l, SW_X, ty, k, 0xFF9A9AA8);
            ty += 9 * k;
        }
    }

    /** Trata cliques da tela de Ajustes. Devolve true se o clique foi usado. */
    private boolean settingsClick(double px, double py) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (inRect(px, py, tabX(i), TAB_Y, tabW(i), TAB_H)) {
                settingsTab = i;
                return true;
            }
        }
        if (settingsTab == 1) {
            for (int i = 0; i < Theme.PALETTE.length; i++) {
                int x = SW_X + i * SW_STEP;
                if (inRect(px, py, x, SW_TOP_Y, SW_W, SW_H)) {
                    Theme.setTop(Theme.PALETTE[i]);
                    return true;
                }
                if (inRect(px, py, x, SW_SIDE_Y, SW_W, SW_H)) {
                    Theme.setSide(Theme.PALETTE[i]);
                    return true;
                }
            }
            return false;
        }
        if (inRect(px, py, BTN_X, BTN_PICK_Y, BTN_W, BTN_H)) {
            pickWallpaper();
            return true;
        }
        if (inRect(px, py, BTN_X, BTN_RESET_Y, BTN_W, BTN_H) && Wallpaper.has()) {
            Wallpaper.clear();
            settingsMsg = Lang.t("settings.reset_done");
            settingsMsgColor = 0xFF9A9AA8;
            return true;
        }
        return false;
    }

    private void drawSettings(DrawContext ctx, double pmx, double pmy) {
        final double k = chatK();
        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);

        // cabecalho
        double hk = k * 1.15;
        drawTextCentered(ctx, Text.literal(Lang.t("app.settings")).asOrderedText(),
                (CHAT_X1 + CHAT_X2) / 2.0, CHAT_HEADER_Y + (CHAT_HEADER_H - 8 * hk) / 2.0 - 0.5, hk, 0xFFFFFFFF);
        ctx.fill(CHAT_X1, CHAT_TOP - 1, CHAT_X2, CHAT_TOP, COLOR_LINE);

        drawSettingsTabs(ctx, pmx, pmy);
        if (settingsTab == 1) {
            drawColorsTab(ctx, pmx, pmy);
            drawBars(ctx);
            return;
        }

        // pre-visualizacao (moldura + imagem atual ou o fundo padrao)
        ctx.fill(PREV_X - 1, PREV_Y - 1, PREV_X + PREV_W + 1, PREV_Y + PREV_H + 1, COLOR_LINE);
        if (Wallpaper.has()) {
            drawWallpaper(ctx, PREV_X, PREV_Y, PREV_W, PREV_H);
        } else {
            ctx.drawTexture(PANEL, PREV_X, PREV_Y, PREV_W, PREV_H, SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, PANEL_W, PANEL_H);
        }

        boolean hoverPick = inRect(pmx, pmy, BTN_X, BTN_PICK_Y, BTN_W, BTN_H);
        boolean hoverReset = inRect(pmx, pmy, BTN_X, BTN_RESET_Y, BTN_W, BTN_H);
        drawButton(ctx, BTN_X, BTN_PICK_Y, BTN_W, BTN_H, picking ? Lang.t("settings.choosing") : Lang.t("settings.pick"), hoverPick, !picking);
        drawButton(ctx, BTN_X, BTN_RESET_Y, BTN_W, BTN_H, Lang.t("settings.reset"), hoverReset, Wallpaper.has());

        // mensagem de estado / dica
        int textW = (int) ((CHAT_X2 - PREV_X) / k);
        String msg = settingsMsg.isEmpty() ? Lang.t("settings.tip") : settingsMsg;
        int color = settingsMsg.isEmpty() ? 0xFF9A9AA8 : settingsMsgColor;
        double ty = PREV_Y + PREV_H + 8;
        for (OrderedText l : this.textRenderer.wrapLines(Text.literal(msg), textW)) {
            drawText(ctx, l, PREV_X, ty, k, color);
            ty += 9 * k;
        }

        // moldura, barra de status e navegacao por cima
        drawBars(ctx);
    }

    /** Abre o seletor de arquivos do sistema (em outra thread, para nao travar o jogo). */
    private void pickWallpaper() {
        if (picking) return;
        picking = true;
        settingsMsg = "";
        Runnable task = () -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(5);
                filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"))
                        .put(stack.UTF8("*.bmp")).put(stack.UTF8("*.gif"));
                filters.flip();
                String start = System.getProperty("user.home", "") + File.separator;
                String res = TinyFileDialogs.tinyfd_openFileDialog(Lang.t("settings.picker_title"), start,
                        filters, Lang.t("settings.picker_filter"), false);
                if (res != null && !res.isEmpty()) {
                    pendingPick = Path.of(res);
                }
            } catch (Throwable t) {
                settingsMsg = Lang.t("settings.picker_unavailable");
                settingsMsgColor = 0xFFFF8080;
            } finally {
                picking = false;
            }
        };
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            task.run();   // no macOS o seletor precisa rodar na thread principal
        } else {
            Thread t = new Thread(task, "tablet-inception-filepicker");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Aplica (na thread de render) a imagem escolhida no seletor ou arrastada. */
    private void applyPendingWallpaper() {
        Path p = pendingPick;
        if (p == null) return;
        pendingPick = null;
        try {
            Wallpaper.setFromFile(p);
            settingsMsg = Lang.t("settings.changed");
            settingsMsgColor = 0xFF80FF90;
        } catch (Exception e) {
            settingsMsg = Lang.t("settings.cant_use", e.getMessage());
            settingsMsgColor = 0xFFFF8080;
        }
    }

    /** Arrastar um arquivo de imagem para a janela (so na tela de Ajustes). */
    public void filesDragged(List<Path> paths) {
        if (view == View.SETTINGS && settingsTab == 0 && paths != null && !paths.isEmpty()) {
            pendingPick = paths.get(0);
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
        return this.client.player != null ? this.client.player.getName().getString() : Lang.t("player.default");
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
                    fill = 0xFF3A2E40;
                    border = 0xFF6A5A72;
                } else {
                    drawHead(ctx, SCUTE_SKIN, CHAT_X1, hy, HEAD);
                    bx = CHAT_X1 + HEAD + 3;
                    fill = 0xFF32323A;
                    border = 0xFF7A7A88;
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
        drawBubbleBox(ctx, ix1, iy1, ix2 - ix1, iy2 - iy1, 0xFF0A0A0C, COLOR_LINE);

        double ik = k;
        double ty = iy1 + (iy2 - iy1 - 8 * ik) / 2.0 + ik / 2.0;
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (chatInput.isEmpty()) {
            drawText(ctx, Text.literal(Lang.t("chat.placeholder")).asOrderedText(), ix1 + 4, ty, ik, 0xFF6C6C78);
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
        drawBars(ctx);
    }

    private void sendChat() {
        if (chatInput.trim().isEmpty()) return;
        ScuteAi.send(chatInput, playerName());
        chatInput = "";
        chatScroll = 0;
    }

    // ------------------------------------------------------------------ Clima

    private void drawWeather(DrawContext ctx) {
        final double k = chatK();
        final double lh = 9 * k;
        final WeatherInfo w = WeatherInfo.current(this.client);

        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);

        // cabecalho
        double hk = k * 1.15;
        drawTextCentered(ctx, Text.literal(Lang.t("app.weather")).asOrderedText(),
                (CHAT_X1 + CHAT_X2) / 2.0, CHAT_HEADER_Y + (CHAT_HEADER_H - 8 * hk) / 2.0 - 0.5, hk, 0xFFFFFFFF);
        ctx.fill(CHAT_X1, CHAT_TOP - 1, CHAT_X2, CHAT_TOP, COLOR_LINE);

        // cena animada (o ceu, o sol/lua, as nuvens e a chuva acompanham o jogo)
        final int sx = CHAT_X1;
        final int sy = CHAT_TOP + 4;
        final int sw = 100;
        final int sh = 60;
        ctx.fill(sx - 1, sy - 1, sx + sw + 1, sy + sh + 1, COLOR_LINE);
        WeatherArt.draw(ctx, sx, sy, sw, sh, w);

        // coluna da direita: titulo, nota (bom/ruim) e dados
        final int rx = sx + sw + 8;
        final int rw = CHAT_X2 - rx;
        double tk = k * 1.5;
        while (tk > k && this.textRenderer.getWidth(w.title) * tk > rw) tk -= 0.05;
        drawText(ctx, Text.literal(w.title).asOrderedText(), rx, sy, tk, 0xFFFFFFFF);

        int badgeY = (int) Math.round(sy + 8 * tk + 5);
        int badgeW = (int) Math.ceil(this.textRenderer.getWidth(w.rating.label()) * k) + 10;
        int badgeH = (int) Math.ceil(8 * k) + 5;
        int dark = 0xFF000000 | (((w.rating.color >> 16 & 0xFF) / 4) << 16)
                | (((w.rating.color >> 8 & 0xFF) / 4) << 8) | ((w.rating.color & 0xFF) / 4);
        drawBubbleBox(ctx, rx, badgeY, badgeW, badgeH, dark, w.rating.color);
        drawTextCentered(ctx, Text.literal(w.rating.label()).asOrderedText(), rx + badgeW / 2.0,
                badgeY + (badgeH - 8 * k) / 2.0 + k / 2.0, k, w.rating.color);

        int maxInfoW = (int) (rw / k);
        double iy = badgeY + badgeH + 5;
        java.util.List<String> infos = new java.util.ArrayList<>();
        infos.add(Lang.t("weather.time", w.time, w.day));
        infos.add(Lang.t("weather.biome", w.biome));
        if (w.hasPrecipitation() || w.kind == WeatherInfo.Kind.OVERCAST) {
            infos.add(Lang.t(w.kind == WeatherInfo.Kind.SNOW ? "weather.snow" : "weather.rain", Math.round(w.intensity * 100)));
        }
        for (String line : infos) {
            drawText(ctx, Text.literal(this.textRenderer.trimToWidth(line, maxInfoW)).asOrderedText(),
                    rx, iy, k, 0xFFC4C4D0);
            iy += lh + 1;
        }

        // comentarios sobre o clima atual
        final int boxX1 = CHAT_X1;
        final int boxX2 = CHAT_X2;
        final int boxY1 = sy + sh + 6;
        final int boxY2 = SCREEN_Y + SCREEN_H - 4;
        drawBubbleBox(ctx, boxX1, boxY1, boxX2 - boxX1, boxY2 - boxY1, 0xFF0A0A0C, COLOR_LINE);

        final int padX = 4;
        int textW = (int) ((boxX2 - boxX1 - 2 * padX - 6) / k);
        java.util.List<OrderedText> lines = new java.util.ArrayList<>();
        for (String c : w.comments) {
            lines.addAll(this.textRenderer.wrapLines(Text.literal("\u2022 " + c), textW));
        }
        int visible = Math.max(1, (int) ((boxY2 - boxY1 - 6) / lh));
        int maxScroll = Math.max(0, lines.size() - visible);
        weatherScroll = Math.max(0, Math.min(maxScroll, weatherScroll));

        double ty = boxY1 + 3 + k / 2.0;
        for (int i = weatherScroll; i < Math.min(lines.size(), weatherScroll + visible); i++) {
            drawText(ctx, lines.get(i), boxX1 + padX, ty, k, 0xFFFFFFFF);
            ty += lh;
        }
        if (maxScroll > 0) {
            // setinhas: ha mais texto acima/abaixo
            if (weatherScroll > 0) {
                drawText(ctx, Text.literal("^").asOrderedText(), boxX2 - 7, boxY1 + 2, k, 0xFF9A9AA8);
            }
            if (weatherScroll < maxScroll) {
                drawText(ctx, Text.literal("v").asOrderedText(), boxX2 - 7, boxY2 - 3 - 8 * k, k, 0xFF9A9AA8);
            }
        }

        // moldura, barra de status e navegacao por cima
        drawBars(ctx);
    }

    // ------------------------------------------------------------------ Notas / Relogio / Musica / Em desenvolvimento

    /** Fundo da tela + cabecalho com o titulo do app (padrao dos apps simples). */
    private void drawScreenFrame(DrawContext ctx, String title) {
        final double k = chatK();
        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);
        double hk = k * 1.15;
        drawTextCentered(ctx, Text.literal(title).asOrderedText(),
                (CHAT_X1 + CHAT_X2) / 2.0, CHAT_HEADER_Y + (CHAT_HEADER_H - 8 * hk) / 2.0 - 0.5, hk, 0xFFFFFFFF);
        ctx.fill(CHAT_X1, CHAT_TOP - 1, CHAT_X2, CHAT_TOP, COLOR_LINE);
    }

    private void drawNotes(DrawContext ctx) {
        final double k = chatK();
        final double lh = 9 * k;
        drawScreenFrame(ctx, Lang.t("app.notes"));

        drawBubbleBox(ctx, NOTES_X1, NOTES_Y1, NOTES_X2 - NOTES_X1, NOTES_Y2 - NOTES_Y1, 0xFF0A0A0C, COLOR_LINE);
        final int padX = 4;
        int textW = (int) ((NOTES_X2 - NOTES_X1 - 2 * padX - 6) / k);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(Notes.text() + (blink ? "_" : " ")), textW);

        int visible = Math.max(1, (int) ((NOTES_Y2 - NOTES_Y1 - 6) / lh));
        int maxScroll = Math.max(0, lines.size() - visible);
        notesScroll = Math.max(0, Math.min(maxScroll, notesScroll));
        int first = Math.max(0, lines.size() - visible - notesScroll);

        double ty = NOTES_Y1 + 3 + k / 2.0;
        for (int i = first; i < Math.min(lines.size(), first + visible); i++) {
            drawText(ctx, lines.get(i), NOTES_X1 + padX, ty, k, 0xFFFFFFFF);
            ty += lh;
        }
        if (maxScroll > 0) {
            if (first > 0) {
                drawText(ctx, Text.literal("^").asOrderedText(), NOTES_X2 - 7, NOTES_Y1 + 2, k, 0xFF9A9AA8);
            }
            if (notesScroll > 0) {
                drawText(ctx, Text.literal("v").asOrderedText(), NOTES_X2 - 7, NOTES_Y2 - 3 - 8 * k, k, 0xFF9A9AA8);
            }
        }
        drawBars(ctx);
    }

    private void drawClock(DrawContext ctx) {
        drawScreenFrame(ctx, Lang.t("app.clock"));
        LocalTime now = LocalTime.now();
        String hh = String.format("%02d", now.getHour());
        String mm = String.format("%02d", now.getMinute());
        String ss = String.format("%02d", now.getSecond());

        // letras nitidas: tamanhos multiplos inteiros de pixels reais
        int cardW = Math.min(CHAT_X2 - CHAT_X1 - 20, 230);
        double wHH = this.textRenderer.getWidth("00");
        double wCol = this.textRenderer.getWidth(":");
        double fit = (cardW - 28) / (2 * wHH + wCol + 0.5 * wHH + 1);
        double k = Math.max(1, Math.floor(Math.min(fit, 6.0) * realS)) / realS;
        double ks = Math.max(1, Math.round(k * realS * 0.5)) / (double) realS;

        int cardH = (int) Math.ceil(8 * k) + 30;
        int cardX = (CHAT_X1 + CHAT_X2) / 2 - cardW / 2;
        int cardY = (CHAT_TOP + SCREEN_Y + SCREEN_H) / 2 - cardH / 2;

        // cartao: borda dupla + brilho suave no topo
        drawBubbleBox(ctx, cardX, cardY, cardW, cardH, 0xFF16161A, 0xFF7A7A88);
        drawBubbleBox(ctx, cardX + 2, cardY + 2, cardW - 4, cardH - 4, 0xFF0E0E11, 0xFF2C2C34);
        ctx.fill(cardX + 5, cardY + 3, cardX + cardW - 5, cardY + 4, 0xFF4A4A58);

        // horas : minutos (os dois pontos piscam) + segundos menores, alinhados pela base
        double totalW = (2 * wHH + wCol) * k + 4 + wHH * ks;
        double x = cardX + (cardW - totalW) / 2.0;
        double y = cardY + 9;
        boolean on = (System.currentTimeMillis() / 500) % 2 == 0;
        drawText(ctx, Text.literal(hh).asOrderedText(), x, y, k, 0xFFFFFFFF);
        x += wHH * k;
        drawText(ctx, Text.literal(":").asOrderedText(), x, y, k, on ? 0xFFFFFFFF : 0xFF3C3C46);
        x += wCol * k;
        drawText(ctx, Text.literal(mm).asOrderedText(), x, y, k, 0xFFFFFFFF);
        x += wHH * k + 4;
        drawText(ctx, Text.literal(ss).asOrderedText(), x, y + 8 * k - 8 * ks, ks, 0xFFB0B0C0);

        // barra dos segundos (com marcas a cada 15 s)
        int bx1 = cardX + 12;
        int bx2 = cardX + cardW - 12;
        int by = cardY + cardH - 10;
        ctx.fill(bx1, by, bx2, by + 3, 0xFF1C1C22);
        int fillW = (int) ((bx2 - bx1) * (now.getSecond() + 1) / 60.0);
        ctx.fill(bx1, by, bx1 + fillW, by + 3, 0xFF5E7BFF);
        for (int i = 0; i <= 4; i++) {
            int tx = Math.min(bx2 - 1, bx1 + (bx2 - bx1) * i / 4);
            ctx.fill(tx, by + 4, tx + 1, by + 6, 0xFF7A7A88);
        }
        drawBars(ctx);
    }

    private int musicVisible() {
        return Math.max(1, (MUS_BOTTOM - MUS_TOP) / MUS_ROW_H);
    }

    private void drawMusic(DrawContext ctx, double pmx, double pmy) {
        final double k = chatK();
        drawScreenFrame(ctx, Lang.t("app.music"));

        final int total = MusicPlayer.TRACKS.length;
        final int visible = musicVisible();
        final int maxScroll = Math.max(0, total - visible);
        musicScroll = Math.max(0, Math.min(maxScroll, musicScroll));
        final boolean playing = MusicPlayer.isPlaying(this.client);
        final int rowX2 = CHAT_X2 - (maxScroll > 0 ? 5 : 0);

        for (int r = 0; r < visible; r++) {
            int i = musicScroll + r;
            if (i >= total) break;
            int y = MUS_TOP + r * MUS_ROW_H;
            boolean cur = playing && i == MusicPlayer.current();
            boolean hover = inRect(pmx, pmy, CHAT_X1, y, rowX2 - CHAT_X1, MUS_ROW_H);
            int fill = cur ? 0xFF2E7D32 : hover ? 0xFF32323A : (i % 2 == 0 ? 0xFF18181C : 0xFF141417);
            ctx.fill(CHAT_X1, y, rowX2, y + MUS_ROW_H - 1, fill);
            MusicPlayer.Track t = MusicPlayer.TRACKS[i];
            double ty = y + (MUS_ROW_H - 1 - 8 * k) / 2.0 + k / 2.0;
            drawText(ctx, Text.literal((cur ? "> " : "") + t.name()).asOrderedText(), CHAT_X1 + 4, ty, k, 0xFFFFFFFF);
            String artist = t.artist();
            double aw = this.textRenderer.getWidth(artist) * k;
            drawText(ctx, Text.literal(artist).asOrderedText(), rowX2 - 4 - aw, ty, k, cur ? 0xFFD0FFD0 : 0xFF9A9AA8);
        }
        if (maxScroll > 0) {
            // barra de rolagem
            int trackH = MUS_BOTTOM - MUS_TOP;
            int barH = Math.max(6, trackH * visible / total);
            int barY = MUS_TOP + (trackH - barH) * musicScroll / maxScroll;
            ctx.fill(CHAT_X2 - 3, MUS_TOP, CHAT_X2, MUS_BOTTOM, 0xFF0E0E11);
            ctx.fill(CHAT_X2 - 3, barY, CHAT_X2, barY + barH, 0xFF7A7A88);
        }

        // barra de baixo: "tocando agora" + botao Parar
        ctx.fill(SCREEN_X, MUS_BOTTOM, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, COLOR_BG);
        ctx.fill(CHAT_X1, MUS_BOTTOM, CHAT_X2, MUS_BOTTOM + 1, COLOR_LINE);
        String status;
        if (playing && MusicPlayer.current() >= 0) {
            long s = MusicPlayer.elapsedSeconds();
            status = Lang.t("music.playing", MusicPlayer.TRACKS[MusicPlayer.current()].name(),
                    (s / 60) + ":" + String.format("%02d", s % 60));
        } else {
            status = Lang.t("music.choose");
        }
        int maxW = (int) ((MUS_STOP_X - CHAT_X1 - 8) / k);
        double ty = MUS_STOP_Y + (MUS_STOP_H - 8 * k) / 2.0 + k / 2.0;
        drawText(ctx, Text.literal(this.textRenderer.trimToWidth(status, maxW)).asOrderedText(),
                CHAT_X1 + 2, ty, k, playing ? 0xFF80FF90 : 0xFF9A9AA8);
        drawButton(ctx, MUS_STOP_X, MUS_STOP_Y, MUS_STOP_W, MUS_STOP_H, Lang.t("music.stop"),
                inRect(pmx, pmy, MUS_STOP_X, MUS_STOP_Y, MUS_STOP_W, MUS_STOP_H), playing);
        drawBars(ctx);
    }

    /** Trata cliques do app Musica. Devolve true se o clique foi usado. */
    private boolean musicClick(double px, double py) {
        if (inRect(px, py, MUS_STOP_X, MUS_STOP_Y, MUS_STOP_W, MUS_STOP_H)) {
            MusicPlayer.stop(this.client);
            return true;
        }
        if (px >= CHAT_X1 && px < CHAT_X2 && py >= MUS_TOP && py < MUS_BOTTOM) {
            int row = (int) ((py - MUS_TOP) / MUS_ROW_H);
            int i = musicScroll + row;
            if (row < musicVisible() && i < MusicPlayer.TRACKS.length) {
                if (MusicPlayer.isPlaying(this.client) && MusicPlayer.current() == i) {
                    MusicPlayer.stop(this.client);      // clicar de novo na faixa tocando para a musica
                } else {
                    MusicPlayer.play(this.client, i);
                }
                return true;
            }
        }
        return false;
    }

    private void drawDev(DrawContext ctx) {
        final double k = chatK();
        drawScreenFrame(ctx, devTitle);
        double tk = k * 1.5;
        int wrapW = (int) ((CHAT_X2 - CHAT_X1 - 10) / tk);
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(Lang.t("dev.text")), wrapW);
        double lh = 9 * tk;
        double y = (CHAT_TOP + SCREEN_Y + SCREEN_H) / 2.0 - lines.size() * lh / 2.0;
        for (OrderedText l : lines) {
            drawTextCentered(ctx, l, (CHAT_X1 + CHAT_X2) / 2.0, y, tk, 0xFFFFFFFF);
            y += lh;
        }
        drawBars(ctx);
    }

    private void drawApp(DrawContext ctx, ChildManager.Status st) {
        ctx.fill(SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H, 0xFF000000);
        if (st == ChildManager.Status.RUNNING && texture != null) {
            ctx.drawTexture(textureId, dx, dy, dw, dh, 0f, 0f, texW, texH, texW, texH);
        } else {
            drawPlaceholder(ctx, st);
        }
        // Moldura, barra de status e navegacao por cima do jogo
        drawBars(ctx);
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
            case OFF -> { l1 = Lang.t("child.off"); l2 = Lang.t("child.click_start"); }
            case STARTING -> {
                int dots = (int) ((System.currentTimeMillis() / 400) % 4);
                l1 = Lang.t("child.starting") + ".".repeat(dots);
                l2 = Lang.t("child.first_time");
            }
            case DEAD -> {
                l1 = Lang.t("child.dead");
                l2 = Lang.t("child.retry");
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
                if (view != View.APP) {
                    if (view == View.MUSIC) MusicPlayer.stop(this.client);   // fechar o app de musica para a musica
                    goHome();       // so o Minecraft tem um processo para encerrar
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
        Notes.save();
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

    /** Informa ao iPad (3D) onde esta o cursor na tela, para os outros jogadores verem a mao arrastando. */
    private void reportCursor(double mx, double my) {
        if (cursorDisabled) {
            TabletClientState.onCursor(0.5f, 0.5f, false);
            return;
        }
        double px = panelX(mx);
        double py = panelY(my);
        boolean inside = px >= SCREEN_X && px <= SCREEN_X + SCREEN_W && py >= SCREEN_Y && py <= SCREEN_Y + SCREEN_H;
        float nx = (float) Math.max(0, Math.min(1, (px - SCREEN_X) / SCREEN_W));
        float ny = (float) Math.max(0, Math.min(1, (py - SCREEN_Y) / SCREEN_H));
        TabletClientState.onCursor(nx, ny, inside);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        reportCursor(mx, my);
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
            if (app == APP_SETTINGS) {
                view = View.SETTINGS;
                return true;
            }
            if (app == APP_WEATHER) {
                view = View.WEATHER;
                weatherScroll = 0;
                return true;
            }
            if (app == APP_CLOCK) {
                view = View.CLOCK;
                return true;
            }
            if (app == APP_NOTES) {
                view = View.NOTES;
                notesScroll = 0;
                return true;
            }
            if (app == APP_MUSIC) {
                view = View.MUSIC;
                return true;
            }
            if (app == 1 || app == 2 || app == APP_BOOKS || app == APP_STORE) {
                devTitle = appName(app);     // Camera, Galeria, Livros e App Store: em desenvolvimento
                view = View.DEV;
                return true;
            }
            if (app >= 0) {
                return true;
            }
            if (view == View.SETTINGS && settingsClick(panelX(mx), panelY(my))) {
                return true;
            }
            if (view == View.MUSIC && musicClick(panelX(mx), panelY(my))) {
                return true;
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
        reportCursor(mx, my);
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
        if (view == View.WEATHER) {
            weatherScroll -= (int) Math.signum(amount);   // clampado no proximo frame
            return true;
        }
        if (view == View.NOTES) {
            notesScroll += (int) Math.signum(amount);     // clampado no proximo frame
            return true;
        }
        if (view == View.MUSIC) {
            musicScroll -= (int) Math.signum(amount);     // clampado no proximo frame
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
        if (view == View.SETTINGS) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) goHome();
            return true;
        }
        if (view == View.WEATHER) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> goHome();
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> weatherScroll--;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> weatherScroll++;
                default -> { }
            }
            return true;
        }
        if (view == View.NOTES) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> goHome();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Notes.append("\n");
                case GLFW.GLFW_KEY_BACKSPACE -> Notes.backspace();
                case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_UP -> notesScroll++;
                case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_DOWN -> notesScroll--;
                case GLFW.GLFW_KEY_V -> {
                    if (Screen.hasControlDown()) {
                        String clip = this.client.keyboard.getClipboard();
                        if (clip != null) {
                            for (char c : clip.replace("\r", "").toCharArray()) {
                                if (c == '\n') Notes.append("\n");
                                else typeNoteChar(c);
                            }
                        }
                    }
                }
                default -> { }
            }
            return true;
        }
        if (view == View.MUSIC) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> goHome();
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_PAGE_UP -> musicScroll--;
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_DOWN -> musicScroll++;
                default -> { }
            }
            return true;
        }
        if (view == View.CLOCK || view == View.DEV) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) goHome();
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
        if (view == View.NOTES) {
            typeNoteChar(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void typeNoteChar(char c) {
        if (net.minecraft.SharedConstants.isValidChar(c)) {
            Notes.append(String.valueOf(c));
        }
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
        TabletClientState.onScreenClosed();
        Notes.save();
        uncapture();
        destroyTexture();
    }
}
