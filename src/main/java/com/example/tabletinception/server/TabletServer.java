package com.example.tabletinception.server;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.net.TabletNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LightBlock;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lado do servidor:
 *  - guarda se cada jogador esta com o iPad ligado e onde esta o cursor (para os outros jogadores verem a mao);
 *  - repassa isso para quem esta vendo o jogador;
 *  - mantem um bloco de luz INVISIVEL (minecraft:light) entre o jogador e o iPad enquanto ele esta ligado.
 */
public final class TabletServer {
    /** Nivel de luz do brilho do iPad (0-15). */
    public static final int LIGHT_LEVEL = 11;
    /** Distancia (blocos) a frente dos olhos onde fica a luz. */
    private static final double LIGHT_DISTANCE = 0.55;

    private static final class Entry {
        boolean on;
        float cx = 0.5f;
        float cy = 0.5f;
        boolean touching;
        ServerWorld lightWorld;
        BlockPos lightPos;
        boolean lightWasWater;
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static int tickCounter;

    private TabletServer() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(TabletNet.C2S_STATE, (server, player, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            server.execute(() -> setOn(player, on));
        });
        ServerPlayNetworking.registerGlobalReceiver(TabletNet.C2S_CURSOR, (server, player, handler, buf, sender) -> {
            float x = buf.readFloat();
            float y = buf.readFloat();
            boolean touching = buf.readBoolean();
            server.execute(() -> setCursor(player, x, y, touching));
        });

        ServerTickEvents.END_SERVER_TICK.register(TabletServer::tick);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ENTRIES.clear());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Entry e : ENTRIES.values()) removeLight(e);
            ENTRIES.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> remove(oldPlayer));
        EntityTrackingEvents.START_TRACKING.register((tracked, watcher) -> {
            if (tracked instanceof ServerPlayerEntity p) {
                Entry e = ENTRIES.get(p.getUuid());
                if (e != null && e.on) sendTo(watcher, p, e);
            }
        });
    }

    // ------------------------------------------------------------------ estado

    private static boolean holdingTablet(ServerPlayerEntity p) {
        return p.getMainHandStack().isOf(TabletInceptionMod.TABLET) || p.getOffHandStack().isOf(TabletInceptionMod.TABLET);
    }

    private static void setOn(ServerPlayerEntity p, boolean on) {
        if (on && (!holdingTablet(p) || !p.isAlive() || p.isSpectator())) return;
        Entry e = ENTRIES.computeIfAbsent(p.getUuid(), k -> new Entry());
        if (e.on == on) return;
        e.on = on;
        if (!on) {
            e.touching = false;
            removeLight(e);
        } else {
            updateLight(p, e);
        }
        broadcast(p, e);
    }

    private static void setCursor(ServerPlayerEntity p, float x, float y, boolean touching) {
        Entry e = ENTRIES.get(p.getUuid());
        if (e == null || !e.on) return;
        e.cx = sane(x);
        e.cy = sane(y);
        e.touching = touching;
        broadcast(p, e);
    }

    private static float sane(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0.5f;
        return Math.max(0f, Math.min(1f, v));
    }

    private static void remove(ServerPlayerEntity p) {
        Entry e = ENTRIES.remove(p.getUuid());
        if (e != null) {
            removeLight(e);
            if (e.on) {
                e.on = false;
                e.touching = false;
                broadcast(p, e);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;
        Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> me = it.next();
            Entry e = me.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(me.getKey());
            if (p == null) {
                removeLight(e);
                it.remove();
                continue;
            }
            if (!e.on) continue;
            if (!p.isAlive() || p.isSpectator() || !holdingTablet(p)) {
                e.on = false;
                e.touching = false;
                removeLight(e);
                broadcast(p, e);
                continue;
            }
            if ((tickCounter & 1) == 0) updateLight(p, e);
        }
    }

    // ------------------------------------------------------------------ rede

    private static void broadcast(ServerPlayerEntity p, Entry e) {
        for (ServerPlayerEntity t : PlayerLookup.tracking(p)) {
            sendTo(t, p, e);
        }
    }

    private static void sendTo(ServerPlayerEntity target, ServerPlayerEntity p, Entry e) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(p.getUuid());
        buf.writeBoolean(e.on);
        buf.writeFloat(e.cx);
        buf.writeFloat(e.cy);
        buf.writeBoolean(e.touching);
        ServerPlayNetworking.send(target, TabletNet.S2C_SYNC, buf);
    }

    // ------------------------------------------------------------------ luz invisivel

    private static boolean canHost(ServerWorld w, BlockPos pos) {
        if (!w.isInBuildLimit(pos) || !w.isChunkLoaded(pos)) return false;
        BlockState s = w.getBlockState(pos);
        if (s.isAir()) return true;
        return s.isOf(Blocks.WATER) && s.get(FluidBlock.LEVEL) == 0;
    }

    private static void updateLight(ServerPlayerEntity p, Entry e) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        BlockPos target = BlockPos.ofFloored(eye.add(look.multiply(LIGHT_DISTANCE)).add(0, -0.2, 0));
        if (!canHost(w, target)) {
            target = BlockPos.ofFloored(eye);
            if (!canHost(w, target)) {
                removeLight(e);
                return;
            }
        }
        if (target.equals(e.lightPos) && w == e.lightWorld) return;
        removeLight(e);
        BlockState cur = w.getBlockState(target);
        boolean water = cur.isOf(Blocks.WATER);
        BlockState light = Blocks.LIGHT.getDefaultState()
                .with(LightBlock.LEVEL_15, LIGHT_LEVEL)
                .with(LightBlock.WATERLOGGED, water);
        w.setBlockState(target, light, Block.NOTIFY_LISTENERS);
        e.lightWorld = w;
        e.lightPos = target;
        e.lightWasWater = water;
    }

    private static void removeLight(Entry e) {
        if (e.lightWorld != null && e.lightPos != null) {
            ServerWorld w = e.lightWorld;
            BlockPos pos = e.lightPos;
            if (w.isChunkLoaded(pos) && w.getBlockState(pos).isOf(Blocks.LIGHT)) {
                BlockState restore = e.lightWasWater ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                w.setBlockState(pos, restore, Block.NOTIFY_ALL);
            }
        }
        e.lightWorld = null;
        e.lightPos = null;
        e.lightWasWater = false;
    }
}
