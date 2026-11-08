package dev.mcinside.virtual;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Geração, carregamento/descarregamento progressivo e acesso a blocos. */
public final class VirtualChunkManager {
    public static final int H = 48;
    private static final int SAND_LEVEL = 20;

    private final int seed;
    private final HashMap<Long, VirtualChunk> loaded = new HashMap<>();
    private final HashMap<Long, VirtualChunk> modified = new HashMap<>(); // chunks editados, descarregados

    public VirtualChunkManager(long seed) { this.seed = (int) (seed ^ (seed >>> 32)); }

    public static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public VirtualChunk getChunk(int cx, int cz) { return loaded.get(key(cx, cz)); }
    public int loadedCount() { return loaded.size(); }

    /** -1 = chunk não carregado; y<0 conta como sólido; y>=H é ar. */
    public int getBlock(int x, int y, int z) {
        if (y < 0) return VirtualBlocks.STONE;
        if (y >= H) return 0;
        VirtualChunk c = loaded.get(key(x >> 4, z >> 4));
        if (c == null) return -1;
        return c.blocks[(((y << 4) + (z & 15)) << 4) + (x & 15)];
    }

    public void setBlock(int x, int y, int z, int block) {
        if (y < 0 || y >= H) return;
        VirtualChunk c = loaded.get(key(x >> 4, z >> 4));
        if (c == null) return;
        c.blocks[(((y << 4) + (z & 15)) << 4) + (x & 15)] = (byte) block;
        c.dirty = true;
    }

    /** Descarrega o que está longe e gera no máximo maxGen chunks (os mais próximos primeiro). */
    public void update(int pcx, int pcz, int radius, int maxGen) {
        Iterator<Map.Entry<Long, VirtualChunk>> it = loaded.entrySet().iterator();
        while (it.hasNext()) {
            VirtualChunk c = it.next().getValue();
            if (Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) > radius + 1) {
                it.remove();
                if (c.dirty) modified.put(key(c.cx, c.cz), c);
            }
        }
        for (int n = 0; n < maxGen; n++) {
            int bestD = Integer.MAX_VALUE, bx = 0, bz = 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int d = dx * dx + dz * dz;
                    if (d > radius * radius + radius || d >= bestD) continue;
                    if (loaded.containsKey(key(pcx + dx, pcz + dz))) continue;
                    bestD = d; bx = pcx + dx; bz = pcz + dz;
                }
            }
            if (bestD == Integer.MAX_VALUE) return;
            generate(bx, bz);
        }
    }

    public void ensureAround(int pcx, int pcz, int r) {
        for (int dz = -r; dz <= r; dz++)
            for (int dx = -r; dx <= r; dx++)
                if (!loaded.containsKey(key(pcx + dx, pcz + dz))) generate(pcx + dx, pcz + dz);
    }

    /** Libera memória ao fechar o tablet: só os chunks editados são mantidos. */
    public void trimToModified() {
        for (VirtualChunk c : loaded.values()) if (c.dirty) modified.put(key(c.cx, c.cz), c);
        loaded.clear();
    }

    // ---------------- geração ----------------

    public int surfaceHeight(int x, int z) {
        double n1 = noise(x / 24.0, z / 24.0);
        double n2 = noise(x / 9.0, z / 9.0);
        return (int) (17 + 9 * n1 + 3 * n2);
    }

    private void generate(int cx, int cz) {
        long k = key(cx, cz);
        VirtualChunk stored = modified.remove(k);
        if (stored != null) { loaded.put(k, stored); return; }

        VirtualChunk c = new VirtualChunk(cx, cz);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int h = surfaceHeight(cx * 16 + lx, cz * 16 + lz);
                boolean sandy = h < SAND_LEVEL;
                for (int y = 0; y <= h; y++) {
                    int b;
                    if (y == h) b = sandy ? VirtualBlocks.SAND : VirtualBlocks.GRASS;
                    else if (y >= h - 3) b = sandy ? VirtualBlocks.SAND : VirtualBlocks.DIRT;
                    else b = VirtualBlocks.STONE;
                    c.blocks[(((y << 4) + lz) << 4) + lx] = (byte) b;
                }
            }
        }
        for (int lz = 3; lz <= 12; lz++) {
            for (int lx = 3; lx <= 12; lx++) {
                int wx = cx * 16 + lx, wz = cz * 16 + lz;
                int hh = VirtualBlocks.hash(wx, seed, wz);
                if (Math.floorMod(hh, 70) != 0) continue;
                int h = surfaceHeight(wx, wz);
                if (c.blocks[(((h << 4) + lz) << 4) + lx] != VirtualBlocks.GRASS) continue;
                plantTree(c, lx, h, lz, 4 + ((hh >>> 8) & 1));
            }
        }
        loaded.put(k, c);
    }

    private void plantTree(VirtualChunk c, int lx, int h, int lz, int th) {
        for (int dy = th - 2; dy <= th + 1; dy++) {
            int r = dy >= th ? 1 : 2;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    setLocal(c, lx + dx, h + dy, lz + dz, VirtualBlocks.LEAVES, false);
                }
            }
        }
        for (int dy = 1; dy <= th; dy++) setLocal(c, lx, h + dy, lz, VirtualBlocks.LOG, true);
    }

    private static void setLocal(VirtualChunk c, int x, int y, int z, int b, boolean force) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y >= H) return;
        int i = (((y << 4) + z) << 4) + x;
        if (force || c.blocks[i] == 0) c.blocks[i] = (byte) b;
    }

    private double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(x0, z0), b = lattice(x0 + 1, z0);
        double c = lattice(x0, z0 + 1), d = lattice(x0 + 1, z0 + 1);
        double top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return top + (bot - top) * fz;
    }

    private double lattice(int x, int z) { return (VirtualBlocks.hash(x, seed, z) & 0xFFFF) / 65535.0; }
}
