package com.example.tabletinception.client.ipad;

import com.example.tabletinception.TabletInceptionMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo do iPad (convertido de ipad.bbmodel para ipad_model.json) desenhado "na mao", sem GeckoLib.
 *
 * Unidades do modelo = pixels do Blockbench (1/16 de bloco), eixo Y para cima, tela voltada para -Z (norte).
 * Animacoes (off / on / play / switch_off) animam apenas a ESCALA dos grupos "screen" e "screen_play".
 */
public final class IpadModel {
    public static final Identifier TEXTURE = new Identifier(TabletInceptionMod.MOD_ID, "textures/entity/ipad.png");
    private static final Identifier DATA = new Identifier(TabletInceptionMod.MOD_ID, "ipad_model.json");

    /** Centro do modelo (em pixels do modelo). */
    public static final float CENTER_X = 0f;
    public static final float CENTER_Y = 5.5f;
    public static final float CENTER_Z = 0f;
    /** Metade da largura / altura do iPad (usado para posicionar as maos). */
    public static final float HALF_W = 9f;

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float FLAT_EPS = 0.004f;

    // ordem das faces: norte, leste, sul, oeste, cima, baixo
    private static final int[][][] CORNERS = {
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
    };
    private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static final class Group {
        String name;
        float ox, oy, oz;
        Group parent;
    }

    private static final class Cube {
        Group group;
        float[] from = new float[3];
        float[] to = new float[3];
        float[] origin = new float[3];
        float[] rot = new float[3];
        float inflate;
        float[][] uv = new float[6][];
    }

    private static final class Anim {
        float length;
        Map<String, float[][]> tracks = new HashMap<>(); // grupo -> {tempo, sx, sy, sz}[]
    }

    private static boolean loaded;
    private static float texW = 64;
    private static float texH = 64;
    private static final Map<String, Group> GROUPS = new HashMap<>();
    private static final List<Cube> CUBES = new ArrayList<>();
    private static final Map<String, Anim> ANIMS = new HashMap<>();

    private IpadModel() {
    }

    // ------------------------------------------------------------------ carregamento

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (Reader r = new InputStreamReader(
                MinecraftClient.getInstance().getResourceManager().open(DATA), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            texW = root.get("texW").getAsFloat();
            texH = root.get("texH").getAsFloat();

            JsonObject groups = root.getAsJsonObject("groups");
            for (Map.Entry<String, JsonElement> e : groups.entrySet()) {
                Group g = new Group();
                g.name = e.getKey();
                JsonArray o = e.getValue().getAsJsonObject().getAsJsonArray("origin");
                g.ox = o.get(0).getAsFloat();
                g.oy = o.get(1).getAsFloat();
                g.oz = o.get(2).getAsFloat();
                GROUPS.put(g.name, g);
            }
            for (Map.Entry<String, JsonElement> e : groups.entrySet()) {
                JsonElement p = e.getValue().getAsJsonObject().get("parent");
                if (p != null && !p.isJsonNull()) {
                    GROUPS.get(e.getKey()).parent = GROUPS.get(p.getAsString());
                }
            }

            for (JsonElement ce : root.getAsJsonArray("cubes")) {
                JsonObject co = ce.getAsJsonObject();
                Cube c = new Cube();
                JsonElement g = co.get("group");
                if (g != null && !g.isJsonNull()) c.group = GROUPS.get(g.getAsString());
                readVec(co.getAsJsonArray("from"), c.from);
                readVec(co.getAsJsonArray("to"), c.to);
                readVec(co.getAsJsonArray("origin"), c.origin);
                readVec(co.getAsJsonArray("rot"), c.rot);
                c.inflate = co.get("inflate").getAsFloat();
                JsonArray uv = co.getAsJsonArray("uv");
                for (int i = 0; i < 6; i++) {
                    JsonArray f = uv.get(i).getAsJsonArray();
                    c.uv[i] = new float[]{f.get(0).getAsFloat(), f.get(1).getAsFloat(),
                            f.get(2).getAsFloat(), f.get(3).getAsFloat()};
                }
                CUBES.add(c);
            }

            JsonObject anims = root.getAsJsonObject("anims");
            for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
                JsonObject ao = e.getValue().getAsJsonObject();
                Anim a = new Anim();
                a.length = ao.get("length").getAsFloat();
                for (Map.Entry<String, JsonElement> t : ao.getAsJsonObject("tracks").entrySet()) {
                    JsonArray kfs = t.getValue().getAsJsonArray();
                    float[][] arr = new float[kfs.size()][4];
                    for (int i = 0; i < kfs.size(); i++) {
                        JsonArray kf = kfs.get(i).getAsJsonArray();
                        JsonArray v = kf.get(1).getAsJsonArray();
                        arr[i][0] = kf.get(0).getAsFloat();
                        arr[i][1] = v.get(0).getAsFloat();
                        arr[i][2] = v.get(1).getAsFloat();
                        arr[i][3] = v.get(2).getAsFloat();
                    }
                    a.tracks.put(t.getKey(), arr);
                }
                ANIMS.put(e.getKey(), a);
            }
        } catch (Exception ex) {
            CUBES.clear();
            TabletInceptionMod.LOGGER.error("Nao foi possivel carregar o modelo do iPad ({}): {}", DATA, ex.toString());
        }
    }

    private static void readVec(JsonArray src, float[] dst) {
        for (int i = 0; i < 3; i++) dst[i] = src.get(i).getAsFloat();
    }

    // ------------------------------------------------------------------ animacao

    /** Escala do grupo no instante {@code t} (segundos). Sem keyframes = (1,1,1). Interpolacao linear. */
    private static void sampleScale(String anim, String group, float t, float[] out) {
        out[0] = out[1] = out[2] = 1f;
        Anim a = ANIMS.get(anim);
        if (a == null) return;
        float[][] kf = a.tracks.get(group);
        if (kf == null || kf.length == 0) return;
        if (t <= kf[0][0]) {
            out[0] = kf[0][1];
            out[1] = kf[0][2];
            out[2] = kf[0][3];
            return;
        }
        for (int i = 1; i < kf.length; i++) {
            if (t <= kf[i][0]) {
                float span = kf[i][0] - kf[i - 1][0];
                float f = span <= 1e-6f ? 1f : (t - kf[i - 1][0]) / span;
                for (int k = 0; k < 3; k++) out[k] = kf[i - 1][k + 1] + (kf[i][k + 1] - kf[i - 1][k + 1]) * f;
                return;
            }
        }
        float[] last = kf[kf.length - 1];
        out[0] = last[1];
        out[1] = last[2];
        out[2] = last[3];
    }

    private static Matrix4f groupMatrix(Group g, String anim, float t, Map<Group, Matrix4f> cache) {
        if (g == null) return new Matrix4f();
        Matrix4f m = cache.get(g);
        if (m != null) return m;
        m = new Matrix4f(groupMatrix(g.parent, anim, t, cache));
        float[] s = new float[3];
        sampleScale(anim, g.name, t, s);
        m.translate(g.ox, g.oy, g.oz).scale(s[0], s[1], s[2]).translate(-g.ox, -g.oy, -g.oz);
        cache.put(g, m);
        return m;
    }

    // ------------------------------------------------------------------ desenho

    /**
     * Desenha o iPad na posicao atual da pilha. Origem = origem do modelo (pixels do Blockbench / 16).
     *
     * @param anim  "off", "on", "play" ou "switch_off"
     * @param time  segundos desde o inicio da animacao
     * @param glow  se true a tela (grupos screen/screen_play) e desenhada com brilho maximo
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay,
                              String anim, float time, boolean glow) {
        load();
        if (CUBES.isEmpty()) return;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        Matrix3f nrm = entry.getNormalMatrix();
        Map<Group, Matrix4f> cache = new HashMap<>();
        Vector3f v = new Vector3f();
        Vector3f n = new Vector3f();

        for (Cube c : CUBES) {
            Matrix4f m = new Matrix4f(groupMatrix(c.group, anim, time, cache));
            if (c.rot[0] != 0 || c.rot[1] != 0 || c.rot[2] != 0) {
                m.translate(c.origin[0], c.origin[1], c.origin[2])
                        .rotateZYX((float) Math.toRadians(c.rot[2]), (float) Math.toRadians(c.rot[1]),
                                (float) Math.toRadians(c.rot[0]))
                        .translate(-c.origin[0], -c.origin[1], -c.origin[2]);
            }
            // grupo da tela (screen / screen_play): brilho total e leve avanco para a frente (evita z-fighting)
            boolean screenGroup = c.group != null;
            float zFront = 0f;
            if (c.group != null && c.group.name.equals("screen_play")) zFront = -0.02f;
            int lm = (glow && screenGroup) ? FULL_BRIGHT : light;

            float[] mn = {Math.min(c.from[0], c.to[0]) - c.inflate, Math.min(c.from[1], c.to[1]) - c.inflate,
                    Math.min(c.from[2], c.to[2]) - c.inflate};
            float[] mx = {Math.max(c.from[0], c.to[0]) + c.inflate, Math.max(c.from[1], c.to[1]) + c.inflate,
                    Math.max(c.from[2], c.to[2]) + c.inflate};

            for (int face = 0; face < 6; face++) {
                // ignora faces degeneradas (cubos "planos")
                int axis = (face == 0 || face == 2) ? 2 : (face == 1 || face == 3) ? 0 : 1;
                boolean degenerate = false;
                for (int a = 0; a < 3; a++) {
                    if (a != axis && mx[a] - mn[a] <= 1e-5f) degenerate = true;
                }
                if (degenerate) continue;
                float[] uv = c.uv[face];
                if (Math.abs(uv[2] - uv[0]) < 1e-5f && Math.abs(uv[3] - uv[1]) < 1e-5f) continue;

                boolean flat = mx[axis] - mn[axis] <= 1e-5f;
                float shift = 0f;
                if (flat) {
                    boolean positiveSide = (face == 1 || face == 2 || face == 4);
                    shift = positiveSide ? FLAT_EPS : -FLAT_EPS;
                }
                float[][] uvs = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
                float[] nn = NORMALS[face];
                n.set(nn[0], nn[1], nn[2]);
                nrm.transform(n);

                for (int k = 0; k < 4; k++) {
                    int[] cr = CORNERS[face][k];
                    float x = cr[0] == 1 ? mx[0] : mn[0];
                    float y = cr[1] == 1 ? mx[1] : mn[1];
                    float z = cr[2] == 1 ? mx[2] : mn[2];
                    if (flat) {
                        if (axis == 0) x += shift;
                        else if (axis == 1) y += shift;
                        else z += shift;
                    }
                    v.set(x, y, z);
                    m.transformPosition(v);
                    v.z += zFront;
                    vc.vertex(pos, v.x / 16f, v.y / 16f, v.z / 16f)
                            .color(1f, 1f, 1f, 1f)
                            .texture(uvs[k][0] / texW, uvs[k][1] / texH)
                            .overlay(overlay)
                            .light(lm)
                            .normal(n.x, n.y, n.z)
                            .next();
                }
            }
        }
    }
}
