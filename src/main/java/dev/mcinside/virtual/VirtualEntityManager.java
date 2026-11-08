package dev.mcinside.virtual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class VirtualEntityManager {
    public final List<VirtualEntity> list = new ArrayList<>();
    private final Random rng = new Random(1234);
    private double spawnTimer;

    public void update(double dt, VirtualWorld w, int max, int radiusChunks) {
        if (max <= 0) { list.clear(); return; }
        double lim = radiusChunks * 16.0;
        VirtualPlayer p = w.player;
        for (int i = list.size() - 1; i >= 0; i--) {
            VirtualEntity e = list.get(i);
            double dx = e.x - p.x, dz = e.z - p.z;
            if (dx * dx + dz * dz > lim * lim || list.size() > max) list.remove(i);
        }
        spawnTimer -= dt;
        if (spawnTimer <= 0 && list.size() < max) { spawnTimer = 0.7; trySpawn(w, radiusChunks); }
        for (VirtualEntity e : list) e.update(dt, w.chunks, rng);
    }

    private void trySpawn(VirtualWorld w, int radiusChunks) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double dist = 8 + rng.nextDouble() * Math.max(1, radiusChunks * 16 - 12);
        double x = w.player.x + Math.cos(ang) * dist, z = w.player.z + Math.sin(ang) * dist;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int y = VirtualChunkManager.H - 1; y >= 0; y--) {
            int b = w.chunks.getBlock(bx, y, bz);
            if (b < 0) return;
            if (b == 0) continue;
            if (b == VirtualBlocks.GRASS || b == VirtualBlocks.SAND) list.add(new VirtualEntity(x, y + 1, z));
            return;
        }
    }
}
