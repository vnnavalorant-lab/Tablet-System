import dev.mcinside.virtual.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CoreTest {
    static void check(boolean ok, String msg) { java.lang.System.out.println((ok ? "[OK]   " : "[FAIL] ") + msg); if (!ok) CoreTest.exitCode = 1; }
    static int exitCode = 0;

    static void save(VirtualRenderer r, int w, int h, String name) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] p = r.pixels();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int abgr = p[y * VirtualRenderer.MAX_W + x];
            int rr = abgr & 255, gg = (abgr >> 8) & 255, bb = (abgr >> 16) & 255;
            img.setRGB(x, y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
        }
        ImageIO.write(img, "png", new File("/tmp/mci_out_" + name + ".png"));
    }

    public static void main(String[] a) throws Exception {
        java.lang.System.out.println("== Teste do núcleo ==");
        VirtualWorld w = new VirtualWorld(42L);
        VirtualRenderer r = new VirtualRenderer();

        // física: cair no chão e ficar parado
        w.player.y += 10;
        for (int i = 0; i < 100; i++) w.update(0.05, 0, 0, false, 4, 0);
        double y1 = w.player.y;
        for (int i = 0; i < 20; i++) w.update(0.05, 0, 0, false, 4, 0);
        check(w.player.onGround && Math.abs(w.player.y - y1) < 1e-6, "jogador cai e repousa no chão (y=" + w.player.y + ")");
        check(Math.abs(w.player.y - Math.round(w.player.y)) < 1e-6, "pés alinhados ao topo do bloco");

        // pulo
        double ground = w.player.y; double maxY = ground;
        w.update(0.05, 0, 0, true, 4, 0);
        for (int i = 0; i < 20; i++) { w.update(0.05, 0, 0, false, 4, 0); maxY = Math.max(maxY, w.player.y); }
        check(maxY - ground > 0.9 && maxY - ground < 1.4, "altura do pulo ~1.1 bloco (" + (maxY - ground) + ")");

        // andar para frente e carregar chunks progressivamente
        for (int i = 0; i < 400; i++) w.update(0.05, 1, 0, i % 20 == 0, 4, 0);
        check(w.chunks.loadedCount() > 9 && w.chunks.loadedCount() <= 121, "chunks carregados (limite 121 p/ raio 4): " + w.chunks.loadedCount());
        check(w.player.y > -5, "jogador não caiu no vazio");

        // quebrar / colocar
        w.player.pitch = -0.6; w.update(0.0, 0, 0, false, 4, 0);
        check(w.target != null, "raycast encontra bloco alvo");
        if (w.target != null) {
            int bx = w.target[0], by = w.target[1], bz = w.target[2];
            int before = w.chunks.getBlock(bx, by, bz);
            w.breakBlock();
            check(before > 0 && w.chunks.getBlock(bx, by, bz) == 0, "quebrar bloco");
            w.inventory.select(2);
            w.updateTarget();
            if (w.target != null) {
                int px = w.target[3], py = w.target[4], pz = w.target[5];
                w.placeBlock();
                check(w.chunks.getBlock(px, py, pz) == VirtualBlocks.STONE || w.player.intersectsBlock(px, py, pz), "colocar bloco");
            }
        }

        // trim mantém chunks editados
        int editedBlock = w.chunks.getBlock((int)Math.floor(w.player.x), 0, (int)Math.floor(w.player.z));
        w.chunks.trimToModified();
        check(w.chunks.loadedCount() == 0, "trim libera chunks carregados");
        w.chunks.update((int)Math.floor(w.player.x) >> 4, (int)Math.floor(w.player.z) >> 4, 2, 50);
        check(w.chunks.loadedCount() > 0, "recarrega chunks depois do trim");

        // renderização + tempo por resolução
        w.chunks.update((int)Math.floor(w.player.x) >> 4, (int)Math.floor(w.player.z) >> 4, 6, 200);
        w.player.pitch = -0.15; w.player.yaw = 0.8;
        for (int i = 0; i < 40; i++) w.update(0.05, 0, 0, false, 6, 8);
        int[][] res = {{128,72},{256,144},{512,288},{854,480}};
        int[] dist = {2,4,6,6};
        for (int i = 0; i < res.length; i++) {
            boolean par = res[i][0] >= 512;
            for (int k = 0; k < 3; k++) r.render(w, res[i][0], res[i][1], dist[i]*16, PerformanceManager.Lighting.HIGH, par); // aquecimento
            long t0 = java.lang.System.nanoTime();
            int N = 10;
            for (int k = 0; k < N; k++) r.render(w, res[i][0], res[i][1], dist[i]*16, PerformanceManager.Lighting.HIGH, par);
            double ms = (java.lang.System.nanoTime() - t0) / 1e6 / N;
            java.lang.System.out.printf("   %dx%d dist=%d chunks paralelo=%b  -> %.2f ms/frame%n", res[i][0], res[i][1], dist[i], par, ms);
            save(r, res[i][0], res[i][1], res[i][0] + "");
        }
        // noite
        w.timeSeconds = VirtualWorld.DAY_LENGTH * 0.75;
        r.render(w, 512, 288, 96, PerformanceManager.Lighting.HIGH, true);
        save(r, 512, 288, "night");
        w.timeSeconds = VirtualWorld.DAY_LENGTH * 0.1;

        // criaturas aparecem?
        for (int i = 0; i < 100; i++) w.update(0.05, 0, 0, false, 4, 8);
        check(w.entities.list.size() > 0, "criaturas spawnam: " + w.entities.list.size());
        w.entities.update(0.05, w, 0, 4);
        check(w.entities.list.isEmpty(), "criaturas desativadas limpam a lista");

        // config
        PerformanceManager pm = new PerformanceManager();
        pm.resolutionIndex = 3;
        pm.resetDynamic();
        check(pm.currentWidth() == 854, "resolução máxima inicial");
        for (int i = 0; i < 200; i++) pm.reportFrame(200); // muito lento
        check(pm.currentWidth() == 128, "resolução dinâmica desce sob carga (" + pm.currentWidth() + ")");
        for (int i = 0; i < 400; i++) pm.reportFrame(0.5);
        check(pm.currentWidth() == 854, "resolução dinâmica volta quando sobra tempo (" + pm.currentWidth() + ")");
        pm.eco = true; 
        check(pm.maxEntities() == 0 && pm.distanceChunks() <= 2 && pm.fps() <= 15, "modo economia limita recursos");

        java.lang.System.exit(CoreTest.exitCode);
    }
}
