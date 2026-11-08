package dev.mcinside.virtual;

/** Corpo físico com AABB (jogador e criaturas): gravidade + colisão por eixo em sub-passos. */
public class VirtualBody {
    public double x, y, z;          // y = pés
    public double vx, vy, vz;
    public final double halfW, height;
    public boolean onGround, blockedH;

    protected VirtualBody(double halfW, double height) { this.halfW = halfW; this.height = height; }

    public static boolean collides(VirtualChunkManager cm, double cx, double y, double cz, double halfW, double height) {
        int x0 = (int) Math.floor(cx - halfW), x1 = (int) Math.floor(cx + halfW - 1e-7);
        int y0 = (int) Math.floor(y), y1 = (int) Math.floor(y + height - 1e-7);
        int z0 = (int) Math.floor(cz - halfW), z1 = (int) Math.floor(cz + halfW - 1e-7);
        for (int yy = y0; yy <= y1; yy++)
            for (int zz = z0; zz <= z1; zz++)
                for (int xx = x0; xx <= x1; xx++)
                    if (cm.getBlock(xx, yy, zz) != 0) return true; // -1 (não carregado) também bloqueia
        return false;
    }

    public boolean intersectsBlock(int bx, int by, int bz) {
        return x - halfW < bx + 1 && x + halfW > bx && y < by + 1 && y + height > by
                && z - halfW < bz + 1 && z + halfW > bz;
    }

    /** Integra velocidade; vx/vz/vy devem estar definidos antes. */
    protected void step(double dt, VirtualChunkManager cm) {
        vy -= 32.0 * dt;
        if (vy < -50) vy = -50;
        double dx = vx * dt, dy = vy * dt, dz = vz * dt;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.1);
        if (n < 1) n = 1;
        dx /= n; dy /= n; dz /= n;
        onGround = false;
        blockedH = false;
        for (int i = 0; i < n; i++) {
            if (dx != 0) {
                if (!collides(cm, x + dx, y, z, halfW, height)) x += dx; else { vx = 0; dx = 0; blockedH = true; }
            }
            if (dz != 0) {
                if (!collides(cm, x, y, z + dz, halfW, height)) z += dz; else { vz = 0; dz = 0; blockedH = true; }
            }
            if (dy != 0) {
                if (!collides(cm, x, y + dy, z, halfW, height)) {
                    y += dy;
                } else {
                    if (dy < 0) { y = Math.floor(y + 1e-9); onGround = true; }
                    vy = 0; dy = 0;
                }
            }
        }
    }
}
