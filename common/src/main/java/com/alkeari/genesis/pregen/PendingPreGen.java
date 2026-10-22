package com.alkeari.genesis.pregen;

public final class PendingPreGen {

    private static volatile int radius = 0;
    private static volatile int concurrency = 0;
    private static volatile boolean pending = false;

    private PendingPreGen() {}

    public static void set(int radius, int concurrency) {
        PendingPreGen.radius      = radius;
        PendingPreGen.concurrency = concurrency;
        PendingPreGen.pending     = true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasPending()     { return pending; }
    public static int     getRadius()      { return radius; }
    public static int     getConcurrency() { return concurrency; }
    public static void    clear()          { pending = false; }
}
