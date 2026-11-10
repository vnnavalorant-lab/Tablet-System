package com.example.tabletinception.client.ipad;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.client.TabletScreen;
import com.example.tabletinception.net.TabletNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado do iPad de cada jogador visto por ESTE cliente (o proprio jogador e os outros).
 *
 * Fluxo de quem usa: clique direito -> animacao "play" na mao (0,5 s) -> abre a tela do tablet ->
 * ao fechar a tela -> animacao "switch_off" -> "off".
 */
public final class TabletClientState {
    public static final float PLAY_LEN = 0.5f;
    public static final float SWITCH_LEN = 0.5f;
    /** Enquanto o cursor se mexeu ha menos que isto, a mao esta "tocando" a tela. */
    private static final long TOUCH_IDLE_MS = 1200;

    public record Anim(String name, float time) {
    }

    public static final Anim OFF = new Anim("off", 0.25f);
    public static final Anim ON = new Anim("on", 0.25f);

    public static final class State {
        public boolean on;
        public boolean toggled;
        public long changedMs;
        // cursor (0..1 dentro da tela), alvo e suavizado
        public float tx = 0.5f;
        public float ty = 0.5f;
        public float cx = 0.5f;
        public float cy = 0.5f;
        public boolean touching;
        /** 0 = mao segurando a lateral, 1 = mao na tela. */
        public float touch;
        long lastSmoothMs;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    // ---- lado de quem usa
    private static int pendingOpenTicks = -1;
    private static float curX = 0.5f;
    private static float curY = 0.5f;
    private static boolean curInside;
    private static long curMoveMs;
    private static float sentX = -1;
    private static float sentY = -1;
    private static boolean sentTouching;
    private static int sendCooldown;

    private TabletClientState() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TabletNet.S2C_SYNC, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            boolean on = buf.readBoolean();
            float x = buf.readFloat();
            float y = buf.readFloat();
            boolean touching = buf.readBoolean();
            client.execute(() -> {
                if (client.player != null && id.equals(client.player.getUuid())) return;
                State s = STATES.computeIfAbsent(id, k -> new State());
                if (s.on != on || !s.toggled) {
                    s.on = on;
                    s.toggled = true;
                    s.changedMs = Util.getMeasuringTimeMs();
                    s.cx = x;
                    s.cy = y;
                }
                s.tx = x;
                s.ty = y;
                s.touching = touching;
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(TabletClientState::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATES.clear();
            pendingOpenTicks = -1;
        });
    }

    // ------------------------------------------------------------------ consulta (renderizacao)

    public static State get(UUID id) {
        return STATES.get(id);
    }

    public static Anim anim(State s) {
        if (s == null || !s.toggled) return OFF;
        float t = (Util.getMeasuringTimeMs() - s.changedMs) / 1000f;
        if (s.on) return t < PLAY_LEN ? new Anim("play", t) : ON;
        return t < SWITCH_LEN ? new Anim("switch_off", t) : OFF;
    }

    /** Suaviza cursor e "toque" (chamar uma vez por quadro por jogador). */
    public static void smooth(State s) {
        long now = Util.getMeasuringTimeMs();
        float dt = s.lastSmoothMs == 0 ? 0f : Math.min(0.25f, (now - s.lastSmoothMs) / 1000f);
        s.lastSmoothMs = now;
        boolean settledOn = s.on && anim(s) == ON;
        float target = (settledOn && s.touching) ? 1f : 0f;
        s.touch += (target - s.touch) * (1f - (float) Math.exp(-dt * 12f));
        float k = 1f - (float) Math.exp(-dt * 20f);
        s.cx += (s.tx - s.cx) * k;
        s.cy += (s.ty - s.cy) * k;
    }

    // ------------------------------------------------------------------ abrir / fechar (jogador local)

    /** Substitui o antigo setScreen direto: toca a animacao "play" e abre a tela depois dela. */
    public static void requestOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || pendingOpenTicks >= 0 || mc.currentScreen instanceof TabletScreen) return;
        setLocalOn(true);
        pendingOpenTicks = Math.max(1, Math.round(PLAY_LEN * 20f));
    }

    /** Chamado por TabletScreen.removed(). */
    public static void onScreenClosed() {
        if (pendingOpenTicks >= 0) return;
        setLocalOn(false);
        curInside = false;
    }

    /** Chamado pela tela do tablet a cada movimento do mouse (coordenadas 0..1 dentro da tela do iPad). */
    public static void onCursor(float nx, float ny, boolean inside) {
        if (Math.abs(nx - curX) > 0.002f || Math.abs(ny - curY) > 0.002f) {
            curMoveMs = Util.getMeasuringTimeMs();
        }
        curX = nx;
        curY = ny;
        curInside = inside;
    }

    private static void setLocalOn(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        State s = STATES.computeIfAbsent(mc.player.getUuid(), k -> new State());
        if (s.on != on || !s.toggled) {
            s.on = on;
            s.toggled = true;
            s.changedMs = Util.getMeasuringTimeMs();
        }
        s.touching = false;
        s.touch = 0f;
        sentTouching = false;
        sentX = -1;
        sentY = -1;
        if (ClientPlayNetworking.canSend(TabletNet.C2S_STATE)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(on);
            ClientPlayNetworking.send(TabletNet.C2S_STATE, buf);
        }
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        State me = STATES.get(mc.player.getUuid());

        if (pendingOpenTicks >= 0) {
            if (!IpadPose.isHolding(mc.player) || mc.currentScreen != null) {
                pendingOpenTicks = -1;
                setLocalOn(false);
            } else if (--pendingOpenTicks <= 0) {
                pendingOpenTicks = -1;
                mc.setScreen(new TabletScreen());
            }
            return;
        }

        if (me == null || !me.on) return;

        // ligado mas sem a tela aberta (ex.: tela fechada por outro motivo) -> desliga
        if (!(mc.currentScreen instanceof TabletScreen)) {
            setLocalOn(false);
            return;
        }

        // envia o cursor para o servidor (a cada 2 ticks, so se mudou)
        boolean touching = curInside && (Util.getMeasuringTimeMs() - curMoveMs) < TOUCH_IDLE_MS;
        me.tx = curX;
        me.ty = curY;
        me.touching = touching;
        if (--sendCooldown > 0) return;
        boolean moved = Math.abs(curX - sentX) > 0.004f || Math.abs(curY - sentY) > 0.004f;
        if ((touching && moved) || touching != sentTouching) {
            if (ClientPlayNetworking.canSend(TabletNet.C2S_CURSOR)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeFloat(curX);
                buf.writeFloat(curY);
                buf.writeBoolean(touching);
                ClientPlayNetworking.send(TabletNet.C2S_CURSOR, buf);
            }
            sentX = curX;
            sentY = curY;
            sentTouching = touching;
            sendCooldown = 2;
        }
    }
}
