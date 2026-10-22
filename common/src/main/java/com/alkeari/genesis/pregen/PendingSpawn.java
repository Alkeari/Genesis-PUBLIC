package com.alkeari.genesis.pregen;

/** The spawn point chosen on the preview map, carried from the creation screen until the new world has loaded. */
public final class PendingSpawn {

    private static volatile int blockX = 0;
    private static volatile int blockZ = 0;
    private static volatile boolean pending = false;

    private PendingSpawn() {}

    public static void set(int blockX, int blockZ) {
        PendingSpawn.blockX  = blockX;
        PendingSpawn.blockZ  = blockZ;
        PendingSpawn.pending = true;
    }

    public static boolean hasPending() { return pending; }
    public static int     getBlockX()  { return blockX; }
    public static int     getBlockZ()  { return blockZ; }
    public static void    clear()      { pending = false; }
}
