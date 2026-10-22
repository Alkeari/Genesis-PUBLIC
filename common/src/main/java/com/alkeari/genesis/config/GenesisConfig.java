package com.alkeari.genesis.config;

public class GenesisConfig {

    /** Number of threads for biome preview sampling. 0 = auto-detect (2–4 based on CPU cores). */
    public static volatile int previewThreads = 0;

    /** Radius in chunks covered by the initial three-pass coarse spiral. */
    public static volatile int previewInitialRadius = 64;

    /** Chunks added to the sampling radius on each expansion step after the initial spiral. */
    public static volatile int previewExpandRadius = 16;

    /** Set to false to disable the biome preview entirely and skip sampling threads. */
    public static volatile boolean previewEnabled = true;

    /** Set to false to stop a right-click on the preview map from choosing the new world's spawn point. */
    public static volatile boolean spawnFromMap = true;

    /** Set to false to stop the maps from finding and marking where structures can start. */
    public static volatile boolean showStructures = true;

    /** Set to false on a server to stop players' world maps from asking it for the world's biomes. */
    public static volatile boolean serverWorldMap = true;

    /** Set to false on a server to keep structure locations out of the regions it sends players' world maps. */
    public static volatile boolean serverWorldMapStructures = true;

    /** Default pre-generation radius in chunks shown in the Genesis screen overlay. */
    public static volatile int pregenDefaultRadius = 128;

    /** Set to false to stop Genesis from checking alkeari.com once per start for a newer version. */
    public static volatile boolean checkForUpdates = true;

    /** Default concurrency window (futures in flight) for pre-generation. */
    public static volatile int pregenDefaultThreads = 4;
}
