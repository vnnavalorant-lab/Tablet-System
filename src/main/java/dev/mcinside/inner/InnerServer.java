package dev.mcinside.inner;

import dev.mcinside.MinecraftInside;
import dev.mcinside.mixin.MinecraftServerAccess;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.UnmodifiableLevelProperties;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Lado servidor (mesmo processo, servidor integrado): cria em tempo de execução uma dimensão
 * "minecraftinside:tablet_world" com terreno vanilla de semente própria. Salva dentro do save atual.
 * Se a criação falhar, cai para um ponto distante do Overworld.
 */
public final class InnerServer {
    public static final String PLAYER_NAME = "Tablet";
    public static final RegistryKey<World> KEY =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier(MinecraftInside.MOD_ID, "tablet_world"));
    public static volatile long seed = 0x5DEECE66DL;
    private static ServerWorld cached;

    private InnerServer() { }

    public static boolean isInnerWorld(RegistryKey<World> k) { return KEY.equals(k); }

    /** Chamar na thread do servidor. */
    public static synchronized ServerWorld getOrCreate(MinecraftServer server) {
        Map<RegistryKey<World>, ServerWorld> worlds = ((MinecraftServerAccess) server).mci$worlds();
        ServerWorld w = worlds.get(KEY);
        if (w != null) return w;
        try {
            seed = server.getSaveProperties().getGeneratorOptions().getSeed() ^ 0x5DEECE66DL;
            ServerWorld overworld = server.getOverworld();
            DimensionOptions opts = server.getRegistryManager().get(RegistryKeys.DIMENSION).get(DimensionOptions.OVERWORLD);
            var props = new UnmodifiableLevelProperties(server.getSaveProperties(), server.getSaveProperties().getMainWorldProperties());
            Object listener = Proxy.newProxyInstance(InnerServer.class.getClassLoader(),
                    new Class<?>[]{net.minecraft.server.WorldGenerationProgressListener.class}, (p, m, a) -> null);
            Object[] pool = {server, Util.getMainWorkerExecutor(), ((MinecraftServerAccess) server).mci$session(), props, KEY,
                    opts, listener, Boolean.FALSE, Long.valueOf(BiomeAccess.hashSeed(seed)), List.of(), Boolean.FALSE, null};
            w = (ServerWorld) construct(ServerWorld.class, pool);
            worlds.put(KEY, w);
            cached = w;
            MinecraftInside.LOGGER.info("Mundo interno criado");
            return w;
        } catch (Throwable t) {
            MinecraftInside.LOGGER.error("Falha ao criar mundo interno; usando Overworld distante", t);
            return null;
        }
    }

    /** Preenche o construtor com o argumento de tipo compatível, em ordem (tolera pequenas mudanças de assinatura). */
    private static Object construct(Class<?> cls, Object[] pool) throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] pt = c.getParameterTypes();
            if (pt.length < 10) continue;
            Object[] args = new Object[pt.length];
            int pi = 0;
            boolean ok = true;
            for (int i = 0; i < pt.length && ok; i++) {
                boolean found = false;
                for (; pi < pool.length; pi++) {
                    Object v = pool[pi];
                    Class<?> t = pt[i];
                    boolean match = v == null ? !t.isPrimitive() : wrap(t).isInstance(v);
                    if (match) { args[i] = v; pi++; found = true; break; }
                }
                ok = found;
            }
            if (ok) { c.setAccessible(true); return c.newInstance(args); }
        }
        throw new IllegalStateException("Construtor de " + cls + " não encontrado");
    }

    private static Class<?> wrap(Class<?> t) {
        if (t == boolean.class) return Boolean.class;
        if (t == long.class) return Long.class;
        if (t == int.class) return Integer.class;
        return t;
    }

    /** Leva o jogador "Tablet" ao mundo interno (ou ponto distante do Overworld no fallback). */
    public static void place(MinecraftServer server, ServerPlayerEntity p) {
        ServerWorld w = getOrCreate(server);
        if (w == null) w = server.getOverworld();
        if (p.getServerWorld() == w && w != server.getOverworld()) return;
        if (w == server.getOverworld() && Math.abs(p.getX()) > 500000) return;
        BlockPos spawn = null;
        int base = w == server.getOverworld() ? 1_000_000 : 0;
        try {
            for (int r = 0; r < 12 && spawn == null; r++) {
                BlockPos c = net.minecraft.world.SpawnLocating.findOverworldSpawn(w, base + (r % 4) * 96 - 144, base + (r / 4) * 96 - 96);
                if (c != null && w.getFluidState(c).isEmpty() && w.getFluidState(c.down()).isEmpty()) spawn = c;
            }
        } catch (Throwable ignored) { }
        if (spawn == null) spawn = new BlockPos(base, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, base, base), base);
        p.teleport(w, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
    }
}
