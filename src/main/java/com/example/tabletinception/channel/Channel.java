package com.example.tabletinception.channel;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Arquivo mapeado em memoria compartilhado entre o Minecraft "pai" (que mostra o tablet)
 * e o Minecraft "filho" (que roda dentro do tablet).
 *
 * Layout:
 *   0..63        cabecalho (magic, seq do frame, largura, altura, estado, cursor, janela, indices do anel, shutdown)
 *   64..         anel de eventos de entrada pai -> filho (RING_CAP registros de RECORD_SIZE bytes)
 *   FRAME_OFFSET frame RGBA (linha 0 = topo) filho -> pai, protegido por seqlock
 */
public final class Channel {
    public static final int MAGIC = 0x54424C54;

    private static final int OFF_MAGIC = 0;
    private static final int OFF_SEQ = 4;
    private static final int OFF_W = 8;
    private static final int OFF_H = 12;
    private static final int OFF_STATE = 16;      // 0 = nada, 1 = filho iniciando, 2 = filho renderizando
    private static final int OFF_LOCKED = 20;     // cursor travado no jogo interno
    private static final int OFF_WIN_W = 24;
    private static final int OFF_WIN_H = 28;
    private static final int OFF_IN_WRITE = 36;
    private static final int OFF_IN_READ = 40;
    private static final int OFF_SHUTDOWN = 44;

    private static final int RING_OFFSET = 64;
    private static final int RECORD_SIZE = 40;
    private static final int RING_CAP = 1024;

    public static final int FRAME_OFFSET = 65536;
    public static final int MAX_W = 1920;
    public static final int MAX_H = 1080;
    public static final int FRAME_CAP = MAX_W * MAX_H * 4;
    public static final int TOTAL = FRAME_OFFSET + FRAME_CAP;

    // Tipos de evento de entrada
    public static final int IN_MOUSE_POS = 1;     // d1 = x, d2 = y (coordenadas da janela interna)
    public static final int IN_MOUSE_DELTA = 2;   // d1 = dx, d2 = dy
    public static final int IN_MOUSE_BUTTON = 3;  // a = botao, b = acao, c = mods
    public static final int IN_MOUSE_SCROLL = 4;  // d1 = horizontal, d2 = vertical
    public static final int IN_KEY = 5;           // a = tecla, b = scancode, c = acao, d = mods
    public static final int IN_CHAR = 6;          // a = codepoint, b = mods

    private static final VarHandle INT = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    public interface InputSink {
        void accept(int type, int a, int b, int c, int d, double d1, double d2);
    }

    private final ByteBuffer buf;

    private Channel(ByteBuffer buf) {
        this.buf = buf;
    }

    /** Pai: cria (ou reaproveita) o arquivo. */
    public static Channel create(Path file) throws IOException {
        Channel c = map(file, true);
        c.reset();
        return c;
    }

    /** Filho: abre o arquivo criado pelo pai. */
    public static Channel open(Path file) throws IOException {
        Channel c = map(file, false);
        if ((int) INT.getOpaque(c.buf, OFF_MAGIC) != MAGIC) {
            throw new IOException("Canal invalido: " + file);
        }
        return c;
    }

    private static Channel map(Path file, boolean create) throws IOException {
        StandardOpenOption[] opts = create
                ? new StandardOpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE}
                : new StandardOpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE};
        try (FileChannel fc = FileChannel.open(file, opts)) {
            ByteBuffer mb = fc.map(FileChannel.MapMode.READ_WRITE, 0, TOTAL);
            mb.order(ByteOrder.nativeOrder());
            return new Channel(mb);
        }
    }

    public void reset() {
        for (int i = 0; i < RING_OFFSET; i += 4) {
            INT.setOpaque(buf, i, 0);
        }
        INT.setRelease(buf, OFF_MAGIC, MAGIC);
    }

    // ---- estado ----
    public int state() { return (int) INT.getOpaque(buf, OFF_STATE); }
    public void setState(int s) { INT.setRelease(buf, OFF_STATE, s); }
    public boolean cursorLocked() { return (int) INT.getOpaque(buf, OFF_LOCKED) != 0; }
    public void setCursorLocked(boolean b) { INT.setOpaque(buf, OFF_LOCKED, b ? 1 : 0); }
    public int windowWidth() { return (int) INT.getOpaque(buf, OFF_WIN_W); }
    public int windowHeight() { return (int) INT.getOpaque(buf, OFF_WIN_H); }
    public void setWindowSize(int w, int h) {
        INT.setOpaque(buf, OFF_WIN_W, w);
        INT.setOpaque(buf, OFF_WIN_H, h);
    }
    public boolean shutdownRequested() { return (int) INT.getOpaque(buf, OFF_SHUTDOWN) != 0; }
    public void requestShutdown() { INT.setRelease(buf, OFF_SHUTDOWN, 1); }

    // ---- entrada (pai escreve, filho le) ----
    public boolean sendInput(int type, int a, int b, int c, int d, double d1, double d2) {
        int w = (int) INT.getOpaque(buf, OFF_IN_WRITE);
        int r = (int) INT.getAcquire(buf, OFF_IN_READ);
        if (w - r >= RING_CAP) {
            return false;
        }
        int base = RING_OFFSET + Math.floorMod(w, RING_CAP) * RECORD_SIZE;
        buf.putInt(base, type);
        buf.putInt(base + 4, a);
        buf.putInt(base + 8, b);
        buf.putInt(base + 12, c);
        buf.putInt(base + 16, d);
        buf.putDouble(base + 24, d1);
        buf.putDouble(base + 32, d2);
        INT.setRelease(buf, OFF_IN_WRITE, w + 1);
        return true;
    }

    public int drainInput(InputSink sink) {
        int r = (int) INT.getOpaque(buf, OFF_IN_READ);
        int w = (int) INT.getAcquire(buf, OFF_IN_WRITE);
        int n = 0;
        while (r != w) {
            int base = RING_OFFSET + Math.floorMod(r, RING_CAP) * RECORD_SIZE;
            int type = buf.getInt(base);
            int a = buf.getInt(base + 4);
            int b = buf.getInt(base + 8);
            int c = buf.getInt(base + 12);
            int d = buf.getInt(base + 16);
            double d1 = buf.getDouble(base + 24);
            double d2 = buf.getDouble(base + 32);
            r++;
            INT.setRelease(buf, OFF_IN_READ, r);
            sink.accept(type, a, b, c, d, d1, d2);
            n++;
        }
        return n;
    }

    // ---- frames (filho escreve, pai le) ----

    /**
     * @param rgba frame lido com glReadPixels (RGBA, linha 0 = base). Ordem de bytes nativa.
     *             Este metodo inverte verticalmente e forca alpha = 255.
     */
    public void writeFrame(ByteBuffer rgba, int w, int h) {
        int s = (int) INT.getOpaque(buf, OFF_SEQ);
        INT.setOpaque(buf, OFF_SEQ, s + 1); // impar: escrevendo
        VarHandle.storeStoreFence();

        INT.setOpaque(buf, OFF_W, w);
        INT.setOpaque(buf, OFF_H, h);

        IntBuffer src = rgba.duplicate().order(ByteOrder.nativeOrder()).position(0).asIntBuffer();
        IntBuffer dst = buf.duplicate().order(ByteOrder.nativeOrder()).position(FRAME_OFFSET).asIntBuffer();
        for (int y = 0; y < h; y++) {
            int sr = (h - 1 - y) * w;
            int dr = y * w;
            for (int x = 0; x < w; x++) {
                dst.put(dr + x, src.get(sr + x) | 0xFF000000);
            }
        }

        INT.setRelease(buf, OFF_SEQ, s + 2); // par: frame completo
    }

    /**
     * Copia o frame mais recente para {@code out} se for diferente de {@code lastSeq}.
     * @param meta recebe {largura, altura, seq}
     */
    public boolean readFrame(ByteBuffer out, int[] meta, int lastSeq) {
        int s1 = (int) INT.getAcquire(buf, OFF_SEQ);
        if ((s1 & 1) != 0 || s1 == 0 || s1 == lastSeq) {
            return false;
        }
        int w = (int) INT.getOpaque(buf, OFF_W);
        int h = (int) INT.getOpaque(buf, OFF_H);
        if (w <= 0 || h <= 0 || w > MAX_W || h > MAX_H) {
            return false;
        }
        int bytes = w * h * 4;
        if (out.capacity() < bytes) {
            return false;
        }
        ByteBuffer src = buf.duplicate().order(ByteOrder.nativeOrder());
        src.limit(FRAME_OFFSET + bytes);
        src.position(FRAME_OFFSET);
        out.clear();
        out.put(src);
        out.flip();
        VarHandle.loadLoadFence();
        int s2 = (int) INT.getOpaque(buf, OFF_SEQ);
        if (s1 != s2) {
            return false;
        }
        meta[0] = w;
        meta[1] = h;
        meta[2] = s1;
        return true;
    }
}
