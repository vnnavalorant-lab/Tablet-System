package dev.mcinside.virtual;

/** Tipos de bloco do mundo interno e texturas procedurais 16x16 (sem arquivos de imagem). */
public final class VirtualBlocks {
    public static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, SAND = 4,
            LOG = 5, LEAVES = 6, PLANKS = 7, COBBLE = 8;

    public static final String[] NAMES = {"Ar", "Grama", "Terra", "Pedra", "Areia",
            "Tronco", "Folhas", "Tabuas", "Pedregulho"};

    private static final int[] BASE = {0x000000, 0x5DA83A, 0x86592D, 0x7F7F7F, 0xDBCF8F,
            0x6B5130, 0x3E8A2A, 0xB08A52, 0x6E6E6E};

    private VirtualBlocks() { }

    public static int baseColor(int block) { return BASE[block]; }

    public static int hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /** face: 0 = topo, 1 = baixo, 2 = lateral. Retorna 0xRRGGBB. */
    public static int texel(int b, int face, int ix, int iy) {
        int base = BASE[b];
        int n = hash(ix, iy + b * 17, face) & 15;
        int var = (n - 8) * 2;
        switch (b) {
            case GRASS:
                if (face == 0) base = BASE[GRASS];
                else if (face == 1) base = BASE[DIRT];
                else base = (iy < 3 || (iy == 3 && (n & 1) == 0)) ? BASE[GRASS] : BASE[DIRT];
                break;
            case LOG:
                if (face != 2) {
                    int ring = Math.max(Math.abs(ix * 2 - 15), Math.abs(iy * 2 - 15)) / 2;
                    base = ((ring & 1) == 0) ? 0xA3824E : 0x86683A;
                    var = n - 8;
                } else {
                    var = ((hash(ix, 0, b) & 15) - 8) * 2;
                }
                break;
            case LEAVES:
                var = (n - 8) * 5;
                if (n < 2) base = 0x2B6B1D;
                break;
            case PLANKS:
                if ((iy & 3) == 3) var -= 22;
                break;
            case COBBLE: {
                int cell = hash(ix >> 2, iy >> 2, 9) & 15;
                var = (cell - 8) * 3 + (n - 8);
                if ((ix & 3) == 3 || (iy & 3) == 3) var -= 18;
                break;
            }
            default:
                break;
        }
        return add(base, var);
    }

    private static int add(int rgb, int d) {
        int r = clamp(((rgb >> 16) & 255) + d);
        int g = clamp(((rgb >> 8) & 255) + d);
        int b = clamp((rgb & 255) + d);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
}
