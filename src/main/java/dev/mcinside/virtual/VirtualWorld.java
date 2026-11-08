package dev.mcinside.virtual;

/** Mundo interno independente: chunks, jogador, inventário, criaturas e hora do dia. */
public final class VirtualWorld {
    public static final double DAY_LENGTH = 480.0; // segundos reais por dia
    public static final double REACH = 5.0;

    public final VirtualChunkManager chunks;
    public final VirtualPlayer player = new VirtualPlayer();
    public final VirtualInventory inventory = new VirtualInventory();
    public final VirtualEntityManager entities = new VirtualEntityManager();
    public double timeSeconds = DAY_LENGTH * 0.1;
    /** {x,y,z, xAnterior,yAnterior,zAnterior} ou null. */
    public int[] target;

    public VirtualWorld(long seed) {
        chunks = new VirtualChunkManager(seed);
        chunks.ensureAround(0, 0, 1);
        player.x = 0.5;
        player.z = 0.5;
        player.y = chunks.surfaceHeight(0, 0) + 1.0;
    }

    public void update(double dt, double forward, double strafe, boolean jump, int radiusChunks, int maxEntities) {
        int pcx = (int) Math.floor(player.x) >> 4, pcz = (int) Math.floor(player.z) >> 4;
        chunks.update(pcx, pcz, radiusChunks, 2);
        player.update(dt, forward, strafe, jump, chunks);
        entities.update(dt, this, maxEntities, radiusChunks);
        timeSeconds += dt;
        updateTarget();
    }

    public void updateTarget() {
        double cp = Math.cos(player.pitch);
        target = raycast(player.x, player.y + VirtualPlayer.EYE, player.z,
                Math.sin(player.yaw) * cp, Math.sin(player.pitch), Math.cos(player.yaw) * cp, REACH);
    }

    public void breakBlock() {
        if (target == null || target[1] <= 0) return;
        chunks.setBlock(target[0], target[1], target[2], VirtualBlocks.AIR);
    }

    public void placeBlock() {
        int b = inventory.selectedBlock();
        if (target == null || b == VirtualBlocks.AIR) return;
        int x = target[3], y = target[4], z = target[5];
        if (y < 0 || y >= VirtualChunkManager.H || chunks.getBlock(x, y, z) != 0) return;
        if (player.intersectsBlock(x, y, z)) return;
        chunks.setBlock(x, y, z, b);
    }

    /** DDA (Amanatides & Woo). dx,dy,dz normalizados. */
    public int[] raycast(double ox, double oy, double oz, double dx, double dy, double dz, double maxDist) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int px = x, py = y, pz = z;
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? 1e30 : Math.abs(1.0 / dx);
        double tdy = dy == 0 ? 1e30 : Math.abs(1.0 / dy);
        double tdz = dz == 0 ? 1e30 : Math.abs(1.0 / dz);
        double tmx = (dx > 0 ? x + 1 - ox : ox - x) * tdx;
        double tmy = (dy > 0 ? y + 1 - oy : oy - y) * tdy;
        double tmz = (dz > 0 ? z + 1 - oz : oz - z) * tdz;
        while (true) {
            if (chunks.getBlock(x, y, z) > 0) return new int[]{x, y, z, px, py, pz};
            px = x; py = y; pz = z;
            double t;
            if (tmx < tmy && tmx < tmz) { t = tmx; x += sx; tmx += tdx; }
            else if (tmy < tmz) { t = tmy; y += sy; tmy += tdy; }
            else { t = tmz; z += sz; tmz += tdz; }
            if (t > maxDist) return null;
        }
    }
}
