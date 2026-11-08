package dev.mcinside.virtual;

public final class VirtualChunk {
    public final int cx, cz;
    public final byte[] blocks = new byte[16 * VirtualChunkManager.H * 16]; // índice: (y*16+z)*16+x
    public boolean dirty;

    public VirtualChunk(int cx, int cz) { this.cx = cx; this.cz = cz; }
}
