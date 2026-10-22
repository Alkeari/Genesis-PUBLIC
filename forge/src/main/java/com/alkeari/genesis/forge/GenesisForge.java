package com.alkeari.genesis.forge;

import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.genesis.config.forge.GenesisForgeConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Genesis.MOD_ID)
@SuppressWarnings("unused")
public class GenesisForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisForge");

    @SuppressWarnings({"removal", "unused"})
    public GenesisForge() {
        LOGGER.info("[GenesisForge] Initializing Genesis (Forge)");
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GenesisForgeConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GenesisForgeConfig.COMMON_SPEC);
        modBus.addListener(GenesisForge::onConfigLoad);
        modBus.addListener(GenesisForge::onConfigReload);
        Genesis.init();
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == GenesisForgeConfig.CLIENT_SPEC) applyConfig();
        if (event.getConfig().getSpec() == GenesisForgeConfig.COMMON_SPEC) applyCommonConfig();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GenesisForgeConfig.CLIENT_SPEC) applyConfig();
        if (event.getConfig().getSpec() == GenesisForgeConfig.COMMON_SPEC) applyCommonConfig();
    }

    private static void applyCommonConfig() {
        GenesisConfig.serverWorldMap = GenesisForgeConfig.SERVER_WORLD_MAP.get();
        GenesisConfig.serverWorldMapStructures = GenesisForgeConfig.SERVER_WORLD_MAP_STRUCTURES.get();
        GenesisConfig.checkForUpdates = GenesisForgeConfig.CHECK_FOR_UPDATES.get();
    }

    private static void applyConfig() {
        GenesisConfig.previewEnabled       = GenesisForgeConfig.PREVIEW_ENABLED.get();
        GenesisConfig.previewThreads       = GenesisForgeConfig.PREVIEW_THREADS.get();
        GenesisConfig.previewInitialRadius = GenesisForgeConfig.PREVIEW_INITIAL_RADIUS.get();
        GenesisConfig.previewExpandRadius  = GenesisForgeConfig.PREVIEW_EXPAND_RADIUS.get();
        GenesisConfig.spawnFromMap         = GenesisForgeConfig.SPAWN_FROM_MAP.get();
        GenesisConfig.showStructures       = GenesisForgeConfig.SHOW_STRUCTURES.get();
        GenesisConfig.pregenDefaultRadius  = GenesisForgeConfig.PREGEN_DEFAULT_RADIUS.get();
        GenesisConfig.pregenDefaultThreads = GenesisForgeConfig.PREGEN_DEFAULT_THREADS.get();
        LOGGER.info("[GenesisForge] Config applied: previewEnabled={} previewThreads={} previewInitial={} previewExpand={} pregenRadius={} pregenThreads={}",
                GenesisConfig.previewEnabled, GenesisConfig.previewThreads,
                GenesisConfig.previewInitialRadius, GenesisConfig.previewExpandRadius,
                GenesisConfig.pregenDefaultRadius, GenesisConfig.pregenDefaultThreads);
    }
}
