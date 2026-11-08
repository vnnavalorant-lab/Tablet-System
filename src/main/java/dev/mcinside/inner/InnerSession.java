package dev.mcinside.inner;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.mcinside.MinecraftInside;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkState;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;

import java.lang.reflect.Constructor;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * O "Minecraft dentro do Minecraft": um segundo cliente vanilla (mundo, jogador, renderizador, HUD)
 * conectado por loopback local ao servidor integrado. Não é outro processo nem outra instância:
 * os campos do MinecraftClient são trocados durante o tick/render/pacotes internos e restaurados depois.
 */
public final class InnerSession {
    public static volatile InnerSession ACTIVE;
    private static boolean inCtx;

    public static boolean inContext() { return inCtx; }
    public static InnerSession current() { return ACTIVE; }

    final MinecraftClient client = MinecraftClient.getInstance();
    final ClientAccess access = (ClientAccess) client;

    // estado do cliente interno
    ClientConnection connection;
    ClientPlayNetworkHandler handler;
    ClientWorld world;
    ClientPlayerEntity player;
    ClientPlayerInteractionManager im;
    Entity camera;
    HitResult crosshair;
    Entity targeted;
    WorldRenderer wr;
    InGameHud hud;
    Screen innerScreen;
    Framebuffer fb;
    TabletInput input;

    private boolean running, joined;
    private GraphicsMode prevGraphics;
    public int ticksSinceJoin;
    public long startMs;

    public boolean isRunning() { return running; }
    public boolean isJoined() { return joined; }
    public Screen innerScreen() { return innerScreen; }
    public ClientPlayerEntity player() { return player; }
    public ClientWorld world() { return world; }
    public Framebuffer framebuffer() { return fb; }

    /** Chamado pelos mixins: este listener é do jogo interno? */
    public boolean owns(Object listener) {
        if (connection == null || !(listener instanceof ClientPlayNetworkHandler h)) return false;
        if (h.getConnection() != connection) return false;
        handler = h;
        return true;
    }

    // ------------------------------------------------------------ contexto

    public void inContext(Runnable r) {
        if (inCtx) { r.run(); return; }
        ClientWorld oW = client.world; ClientPlayerEntity oP = client.player; ClientPlayerInteractionManager oI = client.interactionManager;
        Entity oC = client.getCameraEntity(); HitResult oH = client.crosshairTarget; Entity oT = client.targetedEntity;
        Screen oS = client.currentScreen; WorldRenderer oR = client.worldRenderer; InGameHud oHud = client.inGameHud;
        inCtx = true;
        try {
            client.world = world; client.player = player; client.interactionManager = im;
            client.setCameraEntity(camera); client.crosshairTarget = crosshair; client.targetedEntity = targeted;
            client.currentScreen = innerScreen;
            if (wr != null) access.mci$setWorldRenderer(wr);
            if (hud != null) access.mci$setInGameHud(hud);
            r.run();
        } finally {
            world = client.world; player = client.player; im = client.interactionManager;
            camera = client.getCameraEntity(); crosshair = client.crosshairTarget; targeted = client.targetedEntity;
            innerScreen = client.currentScreen;
            client.world = oW; client.player = oP; client.interactionManager = oI;
            client.setCameraEntity(oC); client.crosshairTarget = oH; client.targetedEntity = oT;
            client.currentScreen = oS;
            access.mci$setWorldRenderer(oR); access.mci$setInGameHud(oHud);
            inCtx = false;
        }
    }

    /** Telas abertas pelo jogo interno (inventário, morte...). */
    public void onInnerSetScreen(Screen s) {
        innerScreen = s;
        client.currentScreen = s;
        if (s != null) {
            Window w = client.getWindow();
            s.init(client, w.getScaledWidth(), w.getScaledHeight());
        }
    }

    public void setScreen(Screen s) { inContext(() -> onInnerSetScreen(s)); }

    public void onInnerDisconnected() { joined = false; running = false; }

    // ------------------------------------------------------------ ciclo de vida

    /** Prepara framebuffer, renderizador e HUD internos (sem conectar ainda). */
    public boolean prepare() {
        if (client.getServer() == null) return false;
        ACTIVE = this;
        try {
            prevGraphics = client.options.getGraphicsMode().getValue();
            if (prevGraphics != GraphicsMode.FANCY) client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);
            Window w = client.getWindow();
            fb = new SimpleFramebuffer(w.getFramebufferWidth(), w.getFramebufferHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
            fb.setTexFilter(9729);
            wr = new WorldRenderer(client, client.getEntityRenderDispatcher(), client.getBlockEntityRenderDispatcher(), client.getBufferBuilders());
            hud = new InGameHud(client, client.getItemRenderer());
            input = new TabletInput(w.getHandle());
            return true;
        } catch (Throwable t) {
            MinecraftInside.LOGGER.error("Falha ao preparar o Minecraft interno", t);
            close();
            return false;
        }
    }

    /** Conecta ao mundo interno (loopback local). */
    public boolean connect() {
        MinecraftServer srv = client.getServer();
        if (srv == null) return false;
        try {
            srv.submit(() -> InnerServer.getOrCreate(srv)).join();
            SocketAddress addr = srv.getNetworkIo().bindLocal();
            connection = ClientConnection.connectLocal(addr);
            running = true; joined = false; ticksSinceJoin = 0;
            startMs = System.currentTimeMillis();
            Consumer<Object> status = t -> { };
            Object login = null;
            for (Constructor<?> c : ClientLoginNetworkHandler.class.getDeclaredConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                Object[] a = new Object[pt.length];
                for (int i = 0; i < pt.length; i++) {
                    if (pt[i] == ClientConnection.class) a[i] = connection;
                    else if (pt[i] == MinecraftClient.class) a[i] = client;
                    else if (pt[i] == boolean.class) a[i] = Boolean.FALSE;
                    else if (pt[i] == Consumer.class) a[i] = status;
                }
                c.setAccessible(true);
                login = c.newInstance(a);
                break;
            }
            connection.setPacketListener((ClientLoginNetworkHandler) login);
            connection.send(new HandshakeC2SPacket(addr.toString(), 0, NetworkState.LOGIN));
            connection.send(new LoginHelloC2SPacket(InnerServer.PLAYER_NAME, Optional.empty()));
            return true;
        } catch (Throwable t) {
            MinecraftInside.LOGGER.error("Falha ao conectar ao mundo interno", t);
            disconnect();
            return false;
        }
    }

    /** Sai do mundo (o servidor salva o jogador) e volta ao estado "menu". */
    public void disconnect() {
        running = false; joined = false;
        ClientConnection c = connection;
        try { if (c != null && c.isOpen()) c.disconnect(net.minecraft.text.Text.literal("Quit")); } catch (Throwable ignored) { }
        try { if (wr != null && world != null) inContext(() -> wr.setWorld(null)); } catch (Throwable ignored) { }
        try { if (client.world != null) client.particleManager.setWorld(client.world); } catch (Throwable ignored) { }
        world = null; player = null; im = null; handler = null; connection = null; camera = null; crosshair = null; targeted = null;
        leftHeld = rightHeld = false;
    }

    /** Encerra tudo e devolve o jogo principal ao normal. */
    public void close() {
        disconnect();
        try { if (wr != null) wr.close(); } catch (Throwable ignored) { }
        try { if (fb != null) fb.delete(); } catch (Throwable ignored) { }
        try { if (prevGraphics != null && prevGraphics != GraphicsMode.FANCY) client.options.getGraphicsMode().setValue(prevGraphics); } catch (Throwable ignored) { }
        wr = null; fb = null; innerScreen = null;
        if (ACTIVE == this) ACTIVE = null;
    }

    // ------------------------------------------------------------ tick

    public void tick() {
        if (!running || connection == null) return;
        inContext(() -> {
            if (im == null) { if (connection.isOpen()) connection.tick(); return; }
            if (player != null && player.input != input) player.input = input;
            input.enabled = innerScreen == null;
            if (world == null || player == null) { im.tick(); return; }
            try {
                im.tick();
                hud.tick(false);
                client.gameRenderer.updateTargetedEntity(1.0f);
                if (innerScreen != null) innerScreen.tick();
                world.tickEntities();
                world.tick(() -> true);
                wr.tick();
                client.particleManager.tick();
                client.gameRenderer.tick();
                actionsTick();
            } catch (Throwable t) { MinecraftInside.LOGGER.warn("Tick interno falhou", t); }
        });
        if (!joined && player != null && world != null) {
            ticksSinceJoin++;
            boolean place = world.getRegistryKey().equals(InnerServer.KEY) || Math.abs(player.getX()) > 500000;
            if ((ticksSinceJoin > 50 && place && world.isChunkLoaded(player.getBlockPos())) || System.currentTimeMillis() - startMs > 25000) joined = true;
        }
    }

    // ------------------------------------------------------------ render

    /** Renderiza um quadro completo (mundo + HUD + tela interna) no framebuffer interno. */
    public void renderFrame(DrawContext outer, float tickDelta) {
        if (fb == null) return;
        boolean inGame = running && world != null && player != null;
        Window w = client.getWindow();
        if (fb.textureWidth != w.getFramebufferWidth() || fb.textureHeight != w.getFramebufferHeight())
            fb.resize(w.getFramebufferWidth(), w.getFramebufferHeight(), MinecraftClient.IS_SYSTEM_MAC);
        outer.draw();
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        var sorter = RenderSystem.getVertexSorting();
        MatrixStack mv = RenderSystem.getModelViewStack();
        Framebuffer outerFb = client.getFramebuffer();
        mv.push();
        try {
            inContext(() -> {
                access.mci$setFramebuffer(fb);
                try {
                    fb.beginWrite(true);
                    RenderSystem.clearColor(0f, 0f, 0f, 1f);
                    RenderSystem.clear(16640, MinecraftClient.IS_SYSTEM_MAC);
                    mv.loadIdentity();
                    RenderSystem.applyModelViewMatrix();
                    if (inGame) client.gameRenderer.renderWorld(tickDelta, System.nanoTime(), new MatrixStack());
                    fb.beginWrite(true);
                    RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                    double sf = w.getScaleFactor();
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, (float) (w.getFramebufferWidth() / sf),
                            (float) (w.getFramebufferHeight() / sf), 0f, 1000f, 21000f), sorter);
                    mv.loadIdentity();
                    mv.translate(0f, 0f, -2000f);
                    RenderSystem.applyModelViewMatrix();
                    DiffuseLighting.enableGuiDepthLighting();
                    DrawContext ctx = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
                    if (inGame && !client.options.hudHidden) hud.render(ctx, tickDelta);
                    RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                    if (innerScreen != null) {
                        int mx = (int) innerMouseX, my = (int) innerMouseY;
                        innerScreen.renderWithTooltip(ctx, mx, my, tickDelta);
                    }
                    ctx.draw();
                } finally {
                    access.mci$setFramebuffer(outerFb);
                }
            });
        } finally {
            outerFb.beginWrite(true);
            mv.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(proj, sorter);
            DiffuseLighting.enableGuiDepthLighting();
        }
    }

    public volatile double innerMouseX, innerMouseY;

    public void attackPressed() { inContext(() -> InnerActions.attackPressed(this)); }
    public void usePressed() { inContext(() -> InnerActions.use(this)); }
    public void selectSlot(int i) { InnerActions.selectSlot(this, i); }
    public void scroll(double a) { InnerActions.scroll(this, a); }
    public void drop(boolean all) { inContext(() -> InnerActions.drop(this, all)); }
    public void swapHands() { InnerActions.swapHands(this); }
    public void openInventory() { inContext(() -> InnerActions.openInventory(this)); }
    public void look(double dx, double dy) { if (player != null) inContext(() -> player.changeLookDirection(dx, dy)); }
    // ações do jogador (implementadas em InnerActions)
    private void actionsTick() { InnerActions.tick(this); }
    public boolean leftHeld, rightHeld;
    int useCooldown;
}
