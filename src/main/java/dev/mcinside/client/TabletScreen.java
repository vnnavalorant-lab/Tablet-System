package dev.mcinside.client;

import dev.mcinside.MinecraftInside;
import dev.mcinside.TabletSystem;
import dev.mcinside.virtual.PerformanceManager;
import dev.mcinside.virtual.VirtualBlocks;
import dev.mcinside.virtual.VirtualRenderer;
import dev.mcinside.virtual.VirtualWorld;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/** Interface do tablet: tela inicial, configurações e o jogo interno. */
public class TabletScreen extends Screen {
    private enum Mode { HOME, SETTINGS, GAME }

    private static final int TEX_W = VirtualRenderer.MAX_W, TEX_H = VirtualRenderer.MAX_H;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final PerformanceManager perf = PerformanceManager.INSTANCE;
    private final VirtualRenderer renderer = new VirtualRenderer();
    private final InputManager input = new InputManager();

    private Mode mode = Mode.HOME;
    private VirtualWorld world;
    private NativeImageBackedTexture texture;
    private Identifier textureId;
    private long lastFrameNs;
    private boolean hasFrame;
    private int frameW, frameH;
    private int sx, sy, sw, sh; // área da tela do tablet

    public TabletScreen() { super(Text.literal("Tablet")); }

    @Override
    protected void init() {
        sw = Math.max(200, Math.min(this.width - 40, (this.height - 40) * 16 / 9));
        sh = sw * 9 / 16;
        sx = (this.width - sw) / 2;
        sy = (this.height - sh) / 2;
        if (texture == null) {
            texture = new NativeImageBackedTexture(TEX_W, TEX_H, true);
            textureId = new Identifier(MinecraftInside.MOD_ID, "virtual_screen");
            this.client.getTextureManager().registerTexture(textureId, texture);
        }
        rebuild();
    }

    private void setMode(Mode m) { mode = m; rebuild(); }

    private void rebuild() {
        this.clearChildren();
        if (mode == Mode.HOME) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Minecraft"), b -> startGame())
                    .dimensions(sx + 24, sy + 40, 90, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Configuracoes"), b -> setMode(Mode.SETTINGS))
                    .dimensions(sx + 124, sy + 40, 90, 20).build());
        } else if (mode == Mode.SETTINGS) {
            addCycle(0, 0, () -> "Resolucao: " + PerformanceManager.RESOLUTIONS[perf.resolutionIndex][0] + "x"
                    + PerformanceManager.RESOLUTIONS[perf.resolutionIndex][1],
                    () -> perf.resolutionIndex = (perf.resolutionIndex + 1) % PerformanceManager.RESOLUTIONS.length);
            addCycle(0, 1, () -> "FPS interno: " + PerformanceManager.FPS_OPTIONS[perf.fpsIndex],
                    () -> perf.fpsIndex = (perf.fpsIndex + 1) % PerformanceManager.FPS_OPTIONS.length);
            addCycle(0, 2, () -> "Distancia: " + PerformanceManager.DISTANCE_OPTIONS[perf.distanceIndex] + " chunks",
                    () -> perf.distanceIndex = (perf.distanceIndex + 1) % PerformanceManager.DISTANCE_OPTIONS.length);
            addCycle(0, 3, () -> "Entidades max.: " + PerformanceManager.ENTITY_OPTIONS[perf.entityIndex],
                    () -> perf.entityIndex = (perf.entityIndex + 1) % PerformanceManager.ENTITY_OPTIONS.length);
            addCycle(1, 0, () -> "Iluminacao: " + perf.lighting.name(),
                    () -> perf.lighting = PerformanceManager.Lighting.values()[(perf.lighting.ordinal() + 1) % 3]);
            addCycle(1, 1, () -> "Criaturas: " + (perf.creatures ? "ON" : "OFF"), () -> perf.creatures = !perf.creatures);
            addCycle(1, 2, () -> "Modo economia: " + (perf.eco ? "ON" : "OFF"), () -> perf.eco = !perf.eco);
            addCycle(1, 3, () -> "Voltar", () -> { perf.save(); setMode(Mode.HOME); });
        }
    }

    private void addCycle(int col, int row, Supplier<String> label, Runnable action) {
        int bw = Math.max(90, sw / 2 - 30);
        int x = sx + 20 + col * (bw + 10), y = sy + 34 + row * 24;
        addDrawableChild(ButtonWidget.builder(Text.literal(label.get()), b -> {
            action.run();
            if (mode == Mode.SETTINGS) b.setMessage(Text.literal(label.get()));
        }).dimensions(x, y, bw, 20).build());
    }

    private void startGame() {
        world = TabletSystem.world();
        mode = Mode.GAME;
        this.clearChildren();
        perf.resetDynamic();
        lastFrameNs = 0;
        hasFrame = false;
        input.capture(this.client.getWindow().getHandle());
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        ctx.fill(sx - 10, sy - 10, sx + sw + 10, sy + sh + 10, 0xFF202024);
        ctx.fill(sx - 2, sy - 2, sx + sw + 2, sy + sh + 2, 0xFF000000);
        if (mode == Mode.GAME) { renderGame(ctx); return; }
        ctx.fill(sx, sy, sx + sw, sy + sh, 0xFF0B1030);
        ctx.drawText(this.textRenderer, LocalTime.now().format(CLOCK), sx + 6, sy + 6, 0xFFFFFF, false);
        ctx.drawText(this.textRenderer, mode == Mode.HOME ? "Apps" : "Configuracoes de desempenho",
                sx + 6, sy + 22, 0xA0B0FF, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderGame(DrawContext ctx) {
        long now = System.nanoTime();
        double interval = 1.0 / perf.fps();
        if (lastFrameNs == 0 || (now - lastFrameNs) / 1e9 >= interval - 0.002) {
            double dt = lastFrameNs == 0 ? 0.0 : Math.min(0.1, (now - lastFrameNs) / 1e9);
            lastFrameNs = now;
            stepAndRender(dt);
        }
        if (hasFrame) {
            ctx.drawTexture(textureId, sx, sy, sw, sh, 0f, 0f, frameW, frameH, TEX_W, TEX_H);
        }
        // mira
        int cx = sx + sw / 2, cy = sy + sh / 2;
        ctx.fill(cx - 4, cy, cx + 5, cy + 1, 0xD0FFFFFF);
        ctx.fill(cx, cy - 4, cx + 1, cy + 5, 0xD0FFFFFF);
        // hotbar
        int slot = 20, total = world.inventory.size() * slot;
        int hx = sx + (sw - total) / 2, hy = sy + sh - slot - 4;
        for (int i = 0; i < world.inventory.size(); i++) {
            int x = hx + i * slot;
            boolean sel = i == world.inventory.selectedIndex();
            ctx.fill(x, hy, x + slot - 2, hy + slot - 2, sel ? 0xFFFFFFFF : 0xFF303030);
            int b = world.inventory.slot(i);
            if (b != VirtualBlocks.AIR) ctx.fill(x + 3, hy + 3, x + slot - 5, hy + slot - 5, 0xFF000000 | VirtualBlocks.baseColor(b));
        }
        ctx.drawText(this.textRenderer, frameW + "x" + frameH + " @" + perf.fps() + "fps  "
                + String.format("%.1f", perf.averageFrameMs()) + "ms  ESC: sair", sx + 4, sy + 4, 0xFFFFFF, true);
    }

    private void stepAndRender(double dt) {
        double[] d = input.pollMouseDelta();
        double s = this.client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double k = s * s * s * 8.0 * 0.15 * Math.PI / 180.0; // mesma curva do jogo
        world.player.yaw -= d[0] * k;
        world.player.pitch = Math.max(-1.55, Math.min(1.55, world.player.pitch - d[1] * k));

        double fwd = (input.key(GLFW.GLFW_KEY_W) ? 1 : 0) - (input.key(GLFW.GLFW_KEY_S) ? 1 : 0);
        double strafe = (input.key(GLFW.GLFW_KEY_D) ? 1 : 0) - (input.key(GLFW.GLFW_KEY_A) ? 1 : 0);
        world.update(dt, fwd, strafe, input.key(GLFW.GLFW_KEY_SPACE), perf.distanceChunks(), perf.maxEntities());

        long t0 = System.nanoTime();
        frameW = perf.currentWidth();
        frameH = perf.currentHeight();
        renderer.render(world, frameW, frameH, perf.distanceChunks() * 16, perf.effectiveLighting(), perf.parallelRender());
        NativeImage img = texture.getImage();
        if (img != null) {
            int[] px = renderer.pixels();
            for (int y = 0; y < frameH; y++)
                for (int x = 0; x < frameW; x++)
                    img.setColor(x, y, px[y * TEX_W + x]);
            texture.upload();
            hasFrame = true;
        }
        perf.reportFrame((System.nanoTime() - t0) / 1e6);
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.GAME) {
            if (button == 0) world.breakBlock();
            else if (button == 1) world.placeBlock();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mode == Mode.GAME) { world.inventory.scroll(amount > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == Mode.GAME && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            world.inventory.select(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.SETTINGS) {
            perf.save();
            setMode(Mode.HOME);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // ESC em HOME/GAME fecha a tela
    }

    @Override
    public void removed() {
        input.release();
        perf.save();
        if (textureId != null) this.client.getTextureManager().destroyTexture(textureId);
        texture = null;
        textureId = null;
        if (TabletSystem.hasWorld()) TabletSystem.world().chunks.trimToModified();
    }
}
