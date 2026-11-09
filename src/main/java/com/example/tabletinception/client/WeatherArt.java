package com.example.tabletinception.client;

import net.minecraft.client.gui.DrawContext;

/**
 * Cena animada (pixel art) do app Clima. So usa retangulos preenchidos, em pixels do painel.
 * O sol e a lua acompanham a hora do jogo; nuvens, chuva, neve e raios acompanham o clima.
 */
final class WeatherArt {
    private static final int GROUND_H = 10;

    private WeatherArt() { }

    static void draw(DrawContext ctx, int x, int y, int w, int h, WeatherInfo i) {
        final long now = System.currentTimeMillis();
        final int skyH = h - GROUND_H;
        final int groundY = y + skyH;

        final boolean nether = i.kind == WeatherInfo.Kind.NETHER;
        final boolean end = i.kind == WeatherInfo.Kind.END;
        final boolean overworld = !nether && !end && i.kind != WeatherInfo.Kind.NONE;
        final double light = overworld ? light(i.t) : 0.5;
        final double precip = (i.kind == WeatherInfo.Kind.RAIN || i.kind == WeatherInfo.Kind.SNOW
                || i.kind == WeatherInfo.Kind.OVERCAST) ? i.intensity : 0;

        // ---------------------------------------------------------------- ceu
        int top;
        int bot;
        if (nether) {
            top = 0x240404;
            bot = 0x8A2008;
        } else if (end) {
            top = 0x07040F;
            bot = 0x2A1244;
        } else if (!overworld) {
            top = 0x101428;
            bot = 0x1C2244;
        } else {
            top = lerp(0x040818, 0x3F8FE0, light);
            bot = lerp(0x0F1D4A, 0x9AD2FF, light);
            double sun = sunset(i.t);
            bot = lerp(bot, 0xF29A52, sun * 0.85);
            top = lerp(top, 0x6A4C93, sun * 0.35);
            if (precip > 0) {
                double dark = i.thunder ? 0.6 : 1.0;
                int gTop = lerp(0x15171E, (int) scale(0x6E7681, dark), light);
                int gBot = lerp(0x1B1E27, (int) scale(0x9AA3AD, dark), light);
                top = lerp(top, gTop, Math.min(1, precip) * 0.9);
                bot = lerp(bot, gBot, Math.min(1, precip) * 0.9);
            }
        }
        ctx.fillGradient(x, y, x + w, groundY, 0xFF000000 | top, 0xFF000000 | bot);

        // ---------------------------------------------------------------- estrelas
        if (end || (overworld && light < 0.45 && precip < 0.6)) {
            double vis = end ? 1.0 : Math.min(1.0, (0.45 - light) / 0.3) * (1 - precip);
            for (int s = 0; s < 22; s++) {
                int sx = x + 2 + (int) (rand(s * 7 + 1) * (w - 4));
                int sy = y + 2 + (int) (rand(s * 7 + 2) * (skyH - 16));
                double tw = 0.55 + 0.45 * Math.sin(now / 420.0 + s * 1.7);
                int a = (int) (255 * vis * tw);
                if (a > 8) ctx.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFFFFFF);
            }
        }

        // ---------------------------------------------------------------- sol e lua
        if (overworld && precip < 0.75) {
            double vis = 1 - precip / 0.75;
            if (i.t < 12000) {
                double p = i.t / 12000.0;
                int cx = x + (int) (w * (0.1 + 0.8 * p));
                int cy = groundY - 3 - (int) (Math.sin(Math.PI * p) * (skyH - 14));
                drawSun(ctx, cx, cy, now, vis, x, x + w, y, groundY);
            } else {
                double p = (i.t - 12000) / 12000.0;
                int cx = x + (int) (w * (0.1 + 0.8 * p));
                int cy = groundY - 3 - (int) (Math.sin(Math.PI * p) * (skyH - 14));
                if (cy < groundY - 4) {
                    int skyAt = lerp(top, bot, Math.max(0, Math.min(1, (cy - y) / (double) skyH)));
                    drawMoon(ctx, cx, cy, i.moonPhase, skyAt, vis, x, x + w, y, groundY);
                }
            }
        }

        // ---------------------------------------------------------------- brasas (Nether)
        if (nether) {
            for (int s = 0; s < 16; s++) {
                int ex = x + 2 + (int) (rand(s * 5 + 3) * (w - 4)) + (int) (Math.sin(now / 700.0 + s) * 2);
                double sp = 6 + rand(s * 5 + 4) * 10;
                int ey = groundY - (int) ((now * sp / 1000.0 + rand(s * 5 + 5) * skyH) % skyH);
                fillClip(ctx, ex, ey, ex + 1, ey + 1, x, x + w, y, groundY, 0xFFFFB347);
            }
        }

        // ---------------------------------------------------------------- nuvens
        if (overworld) {
            boolean heavy = precip > 0.05;
            int count = heavy ? 4 : 2;
            int size = heavy ? 5 : 3;
            int c;
            int shade;
            if (heavy) {
                double dark = i.thunder ? 0.55 : 1.0;
                c = (int) scale(lerp(0x3A3F4A, 0x8D95A0, light), dark);
                shade = (int) scale(c, 0.8);
            } else {
                c = lerp(0x2A3550, 0xFFFFFF, light);
                shade = (int) scale(c, 0.85);
            }
            double speed = heavy ? 7 : 4;
            for (int k = 0; k < count; k++) {
                int period = w + 8 * size;
                int cx = x + (int) (((now * speed / 1000.0) + k * (period / (double) count)) % period) - 7 * size;
                int cy = y + 3 + (k % 2) * (heavy ? 5 : 8) + (heavy ? 0 : 2);
                cloud(ctx, cx, cy, size, 0xFF000000 | c, 0xFF000000 | shade, x, x + w, y, groundY);
            }
        }

        // ---------------------------------------------------------------- raio
        boolean flash = false;
        if (overworld && i.thunder) {
            long cyc = now % 4200;
            long seed = now / 4200;
            flash = cyc < 140 || (cyc > 230 && cyc < 300);
            if (cyc < 320) {
                int bx = x + 14 + (int) (rand((int) seed * 3 + 1) * (w - 28));
                int by = y + 12;
                while (by < groundY) {
                    int seg = Math.min(6, groundY - by);
                    ctx.fill(bx, by, bx + 2, by + seg, 0xFFFFF7B0);
                    by += seg;
                    bx += (int) (rand(by * 13 + (int) seed) * 7) - 3;
                    bx = Math.max(x + 2, Math.min(x + w - 4, bx));
                }
            }
        }

        // ---------------------------------------------------------------- chuva e neve
        if (i.kind == WeatherInfo.Kind.RAIN) {
            int n = (int) (8 + 34 * precip);
            for (int s = 0; s < n; s++) {
                double sp = 55 + rand(s * 3 + 2) * 45;
                int py = (int) ((now * sp / 1000.0 + rand(s * 3 + 3) * skyH) % skyH);
                int px = x + (int) (rand(s * 3 + 1) * w) + (int) (py * 0.22);
                px = x + ((px - x) % w);
                int len = 3;
                fillClip(ctx, px, y + py, px + 1, y + py + len, x, x + w, y, groundY, 0xCC9CC4FF);
            }
        } else if (i.kind == WeatherInfo.Kind.SNOW) {
            int n = (int) (10 + 32 * precip);
            for (int s = 0; s < n; s++) {
                double sp = 9 + rand(s * 3 + 2) * 9;
                int py = (int) ((now * sp / 1000.0 + rand(s * 3 + 3) * skyH) % skyH);
                int px = x + (int) (rand(s * 3 + 1) * w) + (int) (Math.sin(now / 600.0 + s) * 3);
                int sz = rand(s * 11 + 4) > 0.75 ? 2 : 1;
                fillClip(ctx, px, y + py, px + sz, y + py + sz, x, x + w, y, groundY, 0xFFFFFFFF);
            }
        }

        // ---------------------------------------------------------------- chao
        int ground;
        int edge;
        if (nether) {
            ground = 0x4A1414;
            edge = 0xE0501A;
        } else if (end) {
            ground = lerp(0x1E1D16, 0x6C6A52, 0.7);
            edge = 0x8A8868;
        } else if (i.coldBiome) {
            ground = lerp(0x8C97A8, 0xF2F6FF, light);
            edge = lerp(0xA5AFC0, 0xFFFFFF, light);
        } else {
            ground = lerp(0x1E3A18, 0x4C8C3A, light);
            edge = lerp(0x28501F, 0x66B24E, light);
        }
        if (i.dryBiome && overworld) {
            ground = lerp(0x4A402A, 0xD8C58A, light);
            edge = lerp(0x5A4E32, 0xE8D8A0, light);
        }
        ctx.fill(x, groundY, x + w, y + h, 0xFF000000 | ground);
        ctx.fill(x, groundY, x + w, groundY + 1, 0xFF000000 | edge);

        if (overworld) {
            int trunk = (int) scale(0x5A3A1E, 0.35 + 0.65 * light);
            int leaves = i.coldBiome ? lerp(0x7A8898, 0xE8F0FF, light) : lerp(0x15301A, 0x3B7A2C, light);
            if (i.dryBiome) leaves = lerp(0x3A4A22, 0x7C8F3A, light);
            tree(ctx, x + (int) (w * 0.78), groundY, 0xFF000000 | trunk, 0xFF000000 | leaves, 1.0);
            tree(ctx, x + (int) (w * 0.20), groundY, 0xFF000000 | trunk, 0xFF000000 | leaves, 0.7);
        } else if (end) {
            ctx.fill(x + (int) (w * 0.7), groundY - 18, x + (int) (w * 0.7) + 4, groundY, 0xFF14101E);
            ctx.fill(x + (int) (w * 0.25), groundY - 12, x + (int) (w * 0.25) + 3, groundY, 0xFF14101E);
        }

        // ---------------------------------------------------------------- clarao do raio
        if (flash) {
            ctx.fill(x, y, x + w, y + h, 0x70FFFFFF);
        }
    }

    // ------------------------------------------------------------------ peças

    private static void drawSun(DrawContext ctx, int cx, int cy, long now, double vis, int bx1, int bx2, int by1, int by2) {
        int a = (int) (255 * vis);
        double pulse = 0.5 + 0.5 * Math.sin(now / 500.0);
        disk(ctx, cx, cy, 9, ((int) (a * 0.22) << 24) | 0xFFF0A0, bx1, bx2, by1, by2);
        int rayLen = 2 + (int) Math.round(pulse);
        for (int r = 0; r < 8; r++) {
            double ang = r * Math.PI / 4 + now / 6000.0;
            int rx = cx + (int) Math.round(Math.cos(ang) * 8);
            int ry = cy + (int) Math.round(Math.sin(ang) * 8);
            fillClip(ctx, rx, ry, rx + 1 + (rayLen > 2 ? 1 : 0), ry + 1 + (rayLen > 2 ? 1 : 0), bx1, bx2, by1, by2,
                    (a << 24) | 0xFFE680);
        }
        disk(ctx, cx, cy, 6, (a << 24) | 0xFFE680, bx1, bx2, by1, by2);
        disk(ctx, cx, cy, 4, (a << 24) | 0xFFF5B8, bx1, bx2, by1, by2);
    }

    private static void drawMoon(DrawContext ctx, int cx, int cy, int phase, int skyColor, double vis,
                                 int bx1, int bx2, int by1, int by2) {
        int a = (int) (255 * vis);
        disk(ctx, cx, cy, 5, ((int) (a * 0.15) << 24) | 0xC8D8FF, bx1, bx2, by1, by2);
        disk(ctx, cx, cy, 4, (a << 24) | 0xE8EEF8, bx1, bx2, by1, by2);
        int p = phase <= 4 ? phase : 8 - phase;          // 0 = cheia ... 4 = nova
        if (p > 0) {
            double d = (4 - p) * 2.4;
            disk(ctx, cx + (int) Math.round(d), cy - (p == 4 ? 0 : 1), 4, (a << 24) | skyColor, bx1, bx2, by1, by2);
        }
    }

    /** Nuvem em degraus (pixel art). {@code s} e o tamanho do degrau. */
    private static void cloud(DrawContext ctx, int cx, int cy, int s, int c, int shade,
                              int bx1, int bx2, int by1, int by2) {
        fillClip(ctx, cx + 2 * s, cy, cx + 5 * s, cy + s, bx1, bx2, by1, by2, c);
        fillClip(ctx, cx + s, cy + s, cx + 6 * s, cy + 2 * s, bx1, bx2, by1, by2, c);
        fillClip(ctx, cx, cy + 2 * s, cx + 7 * s, cy + 3 * s - 1, bx1, bx2, by1, by2, c);
        fillClip(ctx, cx, cy + 3 * s - 1, cx + 7 * s, cy + 3 * s, bx1, bx2, by1, by2, shade);
    }

    private static void tree(DrawContext ctx, int tx, int groundY, int trunk, int leaves, double sz) {
        int th = (int) Math.round(7 * sz);
        int lw = (int) Math.round(8 * sz);
        int lh = (int) Math.round(7 * sz);
        ctx.fill(tx, groundY - th, tx + 2, groundY, trunk);
        ctx.fill(tx + 1 - lw / 2, groundY - th - lh + 1, tx + 1 + lw / 2, groundY - th + 1, leaves);
        ctx.fill(tx + 1 - lw / 4, groundY - th - lh - 2, tx + 1 + lw / 4, groundY - th - lh + 1, leaves);
    }

    private static void disk(DrawContext ctx, int cx, int cy, int r, int argb, int bx1, int bx2, int by1, int by2) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            fillClip(ctx, cx - half, cy + dy, cx + half + 1, cy + dy + 1, bx1, bx2, by1, by2, argb);
        }
    }

    /** fill() limitado a uma caixa (a cena nao pode desenhar fora do seu quadro). */
    private static void fillClip(DrawContext ctx, int x1, int y1, int x2, int y2,
                                 int bx1, int bx2, int by1, int by2, int argb) {
        int a = Math.max(x1, bx1);
        int b = Math.max(y1, by1);
        int c = Math.min(x2, bx2);
        int d = Math.min(y2, by2);
        if (a < c && b < d) ctx.fill(a, b, c, d, argb);
    }

    // ------------------------------------------------------------------ utilidades

    /** Claridade do ceu pelo tick do dia: 1 = dia, ~0.15 = noite. */
    static double light(long t) {
        if (t < 1000) return 0.55 + 0.45 * (t / 1000.0);
        if (t < 12000) return 1.0;
        if (t < 13000) return 1.0 - 0.85 * ((t - 12000) / 1000.0);
        if (t < 23000) return 0.15;
        return 0.15 + 0.40 * ((t - 23000) / 1000.0);
    }

    /** Peso do tom alaranjado do amanhecer/entardecer (0 a 1). */
    private static double sunset(long t) {
        double a = Math.abs(t - 12500) / 1100.0;
        double b = Math.min(Math.abs(t - 23500), Math.abs(t + 24000 - 23500)) / 1100.0;
        return Math.max(0, 1 - Math.min(a, b));
    }

    private static double rand(int n) {
        n *= 0x9E3779B1;
        n ^= n >>> 15;
        n *= 0x85EBCA6B;
        n ^= n >>> 13;
        return (n & 0xFFFFFF) / (double) 0x1000000;
    }

    private static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static long scale(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
        int b = (int) Math.min(255, (rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }
}
