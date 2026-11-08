package dev.mcinside.virtual;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Renderizador por raycasting de voxels (DDA) na CPU.
 * Escreve pixels ABGR (formato do NativeImage) num buffer de largura fixa MAX_W.
 * Distance culling = alcance máximo do raio; frustum culling = só se traçam raios dentro do campo de visão.
 */
public final class VirtualRenderer {
    public static final int MAX_W = 854, MAX_H = 480;
    private static final double TAN_V = Math.tan(Math.toRadians(35.0));
    private static final int[] ENTITY_RGB = {0xE8, 0xA0, 0xA8};

    private final int[] pixels = new int[MAX_W * MAX_H];

    public int[] pixels() { return pixels; }

    private static final class Frame {
        int w, h;
        double tanX;
        double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz;
        double maxDist;
        int maxSteps;
        PerformanceManager.Lighting lighting;
        double daylight, sunX, sunY, sunZ;
        boolean shadows;
        int[] skyH = new int[3], skyZ = new int[3];
        boolean hasTarget;
        int tx, ty, tz;
        double[] ents;
        int entCount;
        VirtualChunkManager cm;
    }

    private static final class Ctx {
        final VirtualChunkManager cm;
        VirtualChunk cache;
        int ccx = Integer.MAX_VALUE, ccz;
        Ctx(VirtualChunkManager cm) { this.cm = cm; }

        int block(int x, int y, int z) {
            if (y < 0) return VirtualBlocks.STONE;
            if (y >= VirtualChunkManager.H) return 0;
            int cx = x >> 4, cz = z >> 4;
            if (cx != ccx || cz != ccz) { cache = cm.getChunk(cx, cz); ccx = cx; ccz = cz; }
            if (cache == null) return -1;
            return cache.blocks[(((y << 4) + (z & 15)) << 4) + (x & 15)];
        }
    }

    public void render(VirtualWorld world, int w, int h, int maxDistBlocks,
                       PerformanceManager.Lighting lighting, boolean parallel) {
        Frame f = new Frame();
        f.w = w; f.h = h; f.tanX = TAN_V * w / (double) h;
        VirtualPlayer p = world.player;
        f.ex = p.x; f.ey = p.y + VirtualPlayer.EYE; f.ez = p.z;
        double cy = Math.cos(p.yaw), sy = Math.sin(p.yaw), cp = Math.cos(p.pitch), sp = Math.sin(p.pitch);
        f.fx = sy * cp; f.fy = sp; f.fz = cy * cp;
        f.rx = -cy; f.rz = sy;
        f.ux = -sy * sp; f.uy = cp; f.uz = -cy * sp;
        f.maxDist = maxDistBlocks;
        f.maxSteps = maxDistBlocks * 3 + 8;
        f.lighting = lighting;
        f.cm = world.chunks;

        double a = (world.timeSeconds / VirtualWorld.DAY_LENGTH) * 2 * Math.PI;
        double sunY = Math.sin(a), sunX = Math.cos(a), sunZ = 0.25;
        double sl = Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        f.sunX = sunX / sl; f.sunY = sunY / sl; f.sunZ = sunZ / sl;
        double d = Math.max(0.18, Math.min(1.0, 0.5 + 1.2 * sunY));
        f.daylight = d;
        double k = (d - 0.18) / 0.82;
        int[] dayH = {200, 225, 255}, dayZ = {105, 155, 240}, nightH = {20, 25, 50}, nightZ = {5, 6, 18};
        for (int i = 0; i < 3; i++) {
            f.skyH[i] = (int) (nightH[i] + (dayH[i] - nightH[i]) * k);
            f.skyZ[i] = (int) (nightZ[i] + (dayZ[i] - nightZ[i]) * k);
        }
        f.shadows = lighting == PerformanceManager.Lighting.HIGH && sunY > 0.15;

        if (world.target != null) { f.hasTarget = true; f.tx = world.target[0]; f.ty = world.target[1]; f.tz = world.target[2]; }

        List<VirtualEntity> el = world.entities.list;
        f.ents = new double[el.size() * 6];
        double lim2 = (f.maxDist + 1) * (f.maxDist + 1);
        for (VirtualEntity e : el) {
            double ddx = e.x - f.ex, ddz = e.z - f.ez;
            if (ddx * ddx + ddz * ddz > lim2) continue;
            int i = f.entCount++ * 6;
            f.ents[i] = e.x - e.halfW; f.ents[i + 1] = e.y; f.ents[i + 2] = e.z - e.halfW;
            f.ents[i + 3] = e.x + e.halfW; f.ents[i + 4] = e.y + e.height; f.ents[i + 5] = e.z + e.halfW;
        }

        if (parallel) IntStream.range(0, h).parallel().forEach(y -> row(f, y));
        else for (int y = 0; y < h; y++) row(f, y);
    }

    private void row(Frame f, int py) {
        Ctx c = new Ctx(f.cm);
        double sy = (1.0 - (py + 0.5) / f.h * 2.0) * TAN_V;
        int base = py * MAX_W;
        for (int px = 0; px < f.w; px++) {
            double sx = ((px + 0.5) / f.w * 2.0 - 1.0) * f.tanX;
            double dx = f.fx + f.rx * sx + f.ux * sy;
            double dy = f.fy + f.uy * sy;
            double dz = f.fz + f.rz * sx + f.uz * sy;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            pixels[base + px] = trace(f, c, dx / len, dy / len, dz / len);
        }
    }

    private int trace(Frame f, Ctx c, double dx, double dy, double dz) {
        final double ex = f.ex, ey = f.ey, ez = f.ez;
        int x = (int) Math.floor(ex), y = (int) Math.floor(ey), z = (int) Math.floor(ez);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? 1e30 : Math.abs(1.0 / dx);
        double tdy = dy == 0 ? 1e30 : Math.abs(1.0 / dy);
        double tdz = dz == 0 ? 1e30 : Math.abs(1.0 / dz);
        double tmx = (dx > 0 ? x + 1 - ex : ex - x) * tdx;
        double tmy = (dy > 0 ? y + 1 - ey : ey - y) * tdy;
        double tmz = (dz > 0 ? z + 1 - ez : ez - z) * tdz;
        final double maxD = f.maxDist;
        double t = 0;
        int axis = 1, hit = 0;
        for (int i = 0; i < f.maxSteps; i++) {
            int b = c.block(x, y, z);
            if (b > 0) { hit = b; break; }
            if (b < 0) break;
            if (tmx < tmy) {
                if (tmx < tmz) { t = tmx; x += stepX; tmx += tdx; axis = 0; }
                else { t = tmz; z += stepZ; tmz += tdz; axis = 2; }
            } else {
                if (tmy < tmz) { t = tmy; y += stepY; tmy += tdy; axis = 1; }
                else { t = tmz; z += stepZ; tmz += tdz; axis = 2; }
            }
            if (t > maxD) break;
            if (y >= VirtualChunkManager.H && dy >= 0) break;
        }
        double tBlock = hit > 0 ? t : maxD;

        // ---- criaturas (teste ray-AABB) ----
        double bestT = Double.MAX_VALUE;
        int entAxis = -1;
        for (int e = 0; e < f.entCount; e++) {
            int o = e * 6;
            double t0 = 0, t1 = tBlock;
            int ax = -1;
            boolean miss = false;
            double[] lo = {f.ents[o], f.ents[o + 1], f.ents[o + 2]};
            double[] hi = {f.ents[o + 3], f.ents[o + 4], f.ents[o + 5]};
            double[] org = {ex, ey, ez}, dir = {dx, dy, dz};
            for (int k = 0; k < 3; k++) {
                if (dir[k] == 0) {
                    if (org[k] < lo[k] || org[k] > hi[k]) { miss = true; break; }
                } else {
                    double inv = 1.0 / dir[k];
                    double a = (lo[k] - org[k]) * inv, b = (hi[k] - org[k]) * inv;
                    if (a > b) { double tmp = a; a = b; b = tmp; }
                    if (a > t0) { t0 = a; ax = k; }
                    if (b < t1) t1 = b;
                    if (t0 > t1) { miss = true; break; }
                }
            }
            if (!miss && ax >= 0 && t0 < bestT) { bestT = t0; entAxis = ax; }
        }

        int r, g, bl;
        double hitT;
        if (entAxis >= 0) {
            double sh = entAxis == 1 ? 1.0 : entAxis == 0 ? 0.8 : 0.65;
            if (f.lighting == PerformanceManager.Lighting.LOW) sh = 1.0;
            sh *= f.daylight;
            r = (int) (ENTITY_RGB[0] * sh); g = (int) (ENTITY_RGB[1] * sh); bl = (int) (ENTITY_RGB[2] * sh);
            hitT = bestT;
        } else if (hit > 0) {
            double hx = ex + dx * t, hy = ey + dy * t, hz = ez + dz * t;
            double fu, fv;
            int face;
            if (axis == 0) { fu = hz - Math.floor(hz); fv = 1 - (hy - Math.floor(hy)); face = 2; }
            else if (axis == 2) { fu = hx - Math.floor(hx); fv = 1 - (hy - Math.floor(hy)); face = 2; }
            else { fu = hx - Math.floor(hx); fv = hz - Math.floor(hz); face = stepY < 0 ? 0 : 1; }
            int rgb = VirtualBlocks.texel(hit, face, (int) (fu * 16) & 15, (int) (fv * 16) & 15);
            r = (rgb >> 16) & 255; g = (rgb >> 8) & 255; bl = rgb & 255;

            double light = 1.0;
            if (f.lighting != PerformanceManager.Lighting.LOW) {
                light = axis == 1 ? (stepY < 0 ? 1.0 : 0.5) : axis == 0 ? 0.8 : 0.65;
            }
            if (f.shadows) {
                double nx = axis == 0 ? -stepX : 0, ny = axis == 1 ? -stepY : 0, nz = axis == 2 ? -stepZ : 0;
                if (shadowed(f, c, hx + nx * 0.02, hy + ny * 0.02, hz + nz * 0.02)) light *= 0.6;
            }
            light *= f.daylight;
            r = (int) (r * light); g = (int) (g * light); bl = (int) (bl * light);

            if (f.hasTarget && x == f.tx && y == f.ty && z == f.tz) {
                if (fu < 0.04 || fu > 0.96 || fv < 0.04 || fv > 0.96) {
                    r = (int) (r * 0.35); g = (int) (g * 0.35); bl = (int) (bl * 0.35);
                } else {
                    r += (255 - r) / 5; g += (255 - g) / 5; bl += (255 - bl) / 5;
                }
            }
            hitT = t;
        } else {
            return sky(f, dx, dy, dz);
        }

        // neblina (esconde o corte da distância de renderização)
        double fogStart = maxD * 0.6;
        if (hitT > fogStart) {
            double k = Math.min(1.0, (hitT - fogStart) / (maxD - fogStart));
            r += (int) ((f.skyH[0] - r) * k); g += (int) ((f.skyH[1] - g) * k); bl += (int) ((f.skyH[2] - bl) * k);
        }
        return pack(r, g, bl);
    }

    private boolean shadowed(Frame f, Ctx c, double x, double y, double z) {
        for (int i = 1; i <= 16; i++) {
            double s = i * 0.5;
            double py = y + f.sunY * s;
            if (py >= VirtualChunkManager.H) return false;
            if (c.block((int) Math.floor(x + f.sunX * s), (int) Math.floor(py), (int) Math.floor(z + f.sunZ * s)) > 0) return true;
        }
        return false;
    }

    private int sky(Frame f, double dx, double dy, double dz) {
        double k = Math.max(0, Math.min(1, dy * 1.6));
        int r = (int) (f.skyH[0] + (f.skyZ[0] - f.skyH[0]) * k);
        int g = (int) (f.skyH[1] + (f.skyZ[1] - f.skyH[1]) * k);
        int b = (int) (f.skyH[2] + (f.skyZ[2] - f.skyH[2]) * k);
        if (f.sunY > -0.05 && dx * f.sunX + dy * f.sunY + dz * f.sunZ > 0.9975) { r = 255; g = 240; b = 170; }
        return pack(r, g, b);
    }

    private static int pack(int r, int g, int b) {
        r = r < 0 ? 0 : Math.min(r, 255);
        g = g < 0 ? 0 : Math.min(g, 255);
        b = b < 0 ? 0 : Math.min(b, 255);
        return 0xFF000000 | (b << 16) | (g << 8) | r; // ABGR
    }
}
