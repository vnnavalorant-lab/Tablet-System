package dev.mcinside.virtual;

/** Inventário independente (hotbar de 9 slots, blocos infinitos estilo criativo). */
public final class VirtualInventory {
    private final int[] slots = {VirtualBlocks.GRASS, VirtualBlocks.DIRT, VirtualBlocks.STONE,
            VirtualBlocks.SAND, VirtualBlocks.LOG, VirtualBlocks.PLANKS, VirtualBlocks.COBBLE,
            VirtualBlocks.LEAVES, VirtualBlocks.AIR};
    private int selected = 0;

    public int size() { return slots.length; }
    public int slot(int i) { return slots[i]; }
    public int selectedIndex() { return selected; }
    public int selectedBlock() { return slots[selected]; }
    public void select(int i) { if (i >= 0 && i < slots.length) selected = i; }
    public void scroll(int dir) { selected = Math.floorMod(selected + dir, slots.length); }
}
