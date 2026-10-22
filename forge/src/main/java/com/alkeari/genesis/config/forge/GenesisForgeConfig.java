package com.alkeari.genesis.config.forge;

import net.minecraftforge.common.ForgeConfigSpec;

public class GenesisForgeConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.BooleanValue SERVER_WORLD_MAP;
    public static final ForgeConfigSpec.BooleanValue SERVER_WORLD_MAP_STRUCTURES;
    public static final ForgeConfigSpec.BooleanValue CHECK_FOR_UPDATES;
    public static final ForgeConfigSpec.BooleanValue PREVIEW_ENABLED;
    public static final ForgeConfigSpec.IntValue     PREVIEW_THREADS;
    public static final ForgeConfigSpec.IntValue     PREVIEW_INITIAL_RADIUS;
    public static final ForgeConfigSpec.IntValue     PREVIEW_EXPAND_RADIUS;
    public static final ForgeConfigSpec.BooleanValue SPAWN_FROM_MAP;
    public static final ForgeConfigSpec.BooleanValue SHOW_STRUCTURES;
    public static final ForgeConfigSpec.IntValue     PREGEN_DEFAULT_RADIUS;
    public static final ForgeConfigSpec.IntValue     PREGEN_DEFAULT_THREADS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Genesis Biome Preview Settings").push("preview");

        PREVIEW_ENABLED = builder
                .comment("Enable the biome map preview in the Genesis screen.")
                .define("preview_enabled", true);

        PREVIEW_THREADS = builder
                .comment("Threads for biome preview sampling. 0 = auto-detect (2-4 based on CPU cores).")
                .defineInRange("preview_threads", 0, 0, 16);

        PREVIEW_INITIAL_RADIUS = builder
                .comment("Radius in chunks covered by the initial three-pass coarse spiral.")
                .defineInRange("preview_initial_radius", 64, 16, 512);

        PREVIEW_EXPAND_RADIUS = builder
                .comment("Chunks added to the sampling radius on each expansion step.")
                .defineInRange("preview_expand_radius", 16, 4, 128);

        SPAWN_FROM_MAP = builder
                .comment("Right-click the preview map to choose where the new world's spawn point goes.")
                .define("spawn_from_map", true);

        SHOW_STRUCTURES = builder
                .comment("Mark on the maps where structures can start, worked out from the seed.")
                .define("show_structures", true);

        builder.pop();

        builder.comment("Genesis World Pre-Generation Settings").push("pregen");

        PREGEN_DEFAULT_RADIUS = builder
                .comment("Default pre-generation radius in chunks shown in the Genesis screen overlay.")
                .defineInRange("pregen_default_radius", 128, 1, 2048);

        PREGEN_DEFAULT_THREADS = builder
                .comment("Default concurrency window (chunk futures in flight) for pre-generation.")
                .defineInRange("pregen_default_threads", 4, 1, 16);

        builder.pop();
        CLIENT_SPEC = builder.build();

        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        common.comment("Genesis World Map Settings").push("world_map");
        SERVER_WORLD_MAP = common
                .comment("On a server: answer players' world maps with the world's biomes. Off keeps the world's layout from players.")
                .define("server_world_map", true);
        SERVER_WORLD_MAP_STRUCTURES = common
                .comment("On a server: include structure locations in what players' world maps receive.")
                .define("server_world_map_structures", true);
        common.pop();
        common.comment("Genesis Update Settings").push("updates");
        CHECK_FOR_UPDATES = common
                .comment("Once per game start, read https://alkeari.com/updates/genesis.json and say in chat only when a newer Genesis is out for this Minecraft version and loader. Nothing else is sent.")
                .define("check_for_updates", true);
        common.pop();
        COMMON_SPEC = common.build();
    }
}
