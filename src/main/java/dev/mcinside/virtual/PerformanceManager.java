package dev.mcinside.virtual;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configurações de desempenho + resolução dinâmica. Sem dependência do Minecraft. */
public final class PerformanceManager {
    public static final PerformanceManager INSTANCE = new PerformanceManager();

    public enum Lighting { LOW, MEDIUM, HIGH }

    public static final int[][] RESOLUTIONS = {{128, 72}, {256, 144}, {512, 288}, {854, 480}};
    public static final int[] FPS_OPTIONS = {10, 15, 20, 30};
    public static final int[] DISTANCE_OPTIONS = {2, 4, 6};
    public static final int[] ENTITY_OPTIONS = {0, 4, 8, 16};

    // ---- opções persistidas ----
    public int resolutionIndex = 1;
    public int fpsIndex = 2;
    public int distanceIndex = 1;
    public int entityIndex = 1;
    public Lighting lighting = Lighting.MEDIUM;
    public boolean creatures = true;
    public boolean eco = false;

    // ---- estado dinâmico ----
    private int dynIndex = 1;
    private double emaMs = 0;
    private int cooldown = 0;
    private Path file;

    public void init(Path configFile) {
        this.file = configFile;
        load();
        resetDynamic();
    }

    public int resolutionCap() {
        return Math.max(0, eco ? resolutionIndex - 1 : resolutionIndex);
    }

    public int currentWidth() { return RESOLUTIONS[Math.min(dynIndex, resolutionCap())][0]; }
    public int currentHeight() { return RESOLUTIONS[Math.min(dynIndex, resolutionCap())][1]; }
    public int fps() { int f = FPS_OPTIONS[fpsIndex]; return eco ? Math.min(f, 15) : f; }
    public int distanceChunks() { int d = DISTANCE_OPTIONS[distanceIndex]; return eco ? Math.min(d, 2) : d; }
    public int maxEntities() { return (!creatures || eco) ? 0 : ENTITY_OPTIONS[entityIndex]; }
    public Lighting effectiveLighting() { return eco ? Lighting.LOW : lighting; }
    public boolean parallelRender() { return !eco && currentWidth() >= 512; }

    public void resetDynamic() {
        dynIndex = resolutionCap();
        emaMs = 0;
        cooldown = 0;
    }

    /** Resolução dinâmica: reduz se o frame passa de ~70% do orçamento, sobe se sobra muito. */
    public void reportFrame(double ms) {
        emaMs = emaMs == 0 ? ms : emaMs * 0.9 + ms * 0.1;
        if (cooldown > 0) { cooldown--; return; }
        int cap = resolutionCap();
        if (dynIndex > cap) dynIndex = cap;
        double budget = 1000.0 / fps() * 0.7;
        if (emaMs > budget && dynIndex > 0) {
            dynIndex--; cooldown = 20; emaMs = 0;
        } else if (emaMs < budget * 0.2 && dynIndex < cap) {
            dynIndex++; cooldown = 40; emaMs = 0;
        }
    }

    public double averageFrameMs() { return emaMs; }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            resolutionIndex = clamp(Integer.parseInt(p.getProperty("resolution", "1")), 0, RESOLUTIONS.length - 1);
            fpsIndex = clamp(Integer.parseInt(p.getProperty("fps", "2")), 0, FPS_OPTIONS.length - 1);
            distanceIndex = clamp(Integer.parseInt(p.getProperty("distance", "1")), 0, DISTANCE_OPTIONS.length - 1);
            entityIndex = clamp(Integer.parseInt(p.getProperty("entities", "1")), 0, ENTITY_OPTIONS.length - 1);
            lighting = Lighting.valueOf(p.getProperty("lighting", "MEDIUM"));
            creatures = Boolean.parseBoolean(p.getProperty("creatures", "true"));
            eco = Boolean.parseBoolean(p.getProperty("eco", "false"));
        } catch (IOException | RuntimeException e) {
            // arquivo corrompido: mantém os padrões
        }
    }

    public void save() {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("resolution", Integer.toString(resolutionIndex));
        p.setProperty("fps", Integer.toString(fpsIndex));
        p.setProperty("distance", Integer.toString(distanceIndex));
        p.setProperty("entities", Integer.toString(entityIndex));
        p.setProperty("lighting", lighting.name());
        p.setProperty("creatures", Boolean.toString(creatures));
        p.setProperty("eco", Boolean.toString(eco));
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Minecraft Inside Minecraft - desempenho");
        } catch (IOException ignored) { }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
