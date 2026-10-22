package com.alkeari.genesis.neoforge;

import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.genesis.config.neoforge.GenesisNeoForgeConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
//? if <1.20.5
/*import net.neoforged.fml.ModLoadingContext;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Genesis.MOD_ID)
@SuppressWarnings("unused")
public class GenesisNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisNeoForge");

    public GenesisNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[GenesisNeoForge] Initializing Genesis (NeoForge)");
        // ModContainer.registerConfig arrived with NeoForge 20.5; earlier builds register through ModLoadingContext.
        //? if >=1.20.5 {
        modContainer.registerConfig(ModConfig.Type.CLIENT, GenesisNeoForgeConfig.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, GenesisNeoForgeConfig.COMMON_SPEC);
        //?} else {
        /*ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GenesisNeoForgeConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GenesisNeoForgeConfig.COMMON_SPEC);
        *///?}
        modEventBus.addListener(GenesisNeoForge::onConfigLoad);
        modEventBus.addListener(GenesisNeoForge::onConfigReload);
        if (FMLEnvironment.dist.isClient()) {
            GenesisNeoForgeClient.register(modEventBus);
        }
        Genesis.init();
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == GenesisNeoForgeConfig.CLIENT_SPEC) applyConfig();
        if (event.getConfig().getSpec() == GenesisNeoForgeConfig.COMMON_SPEC) applyCommonConfig();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GenesisNeoForgeConfig.CLIENT_SPEC) applyConfig();
        if (event.getConfig().getSpec() == GenesisNeoForgeConfig.COMMON_SPEC) applyCommonConfig();
    }

    private static void applyCommonConfig() {
        GenesisConfig.serverWorldMap = GenesisNeoForgeConfig.SERVER_WORLD_MAP.get();
        GenesisConfig.serverWorldMapStructures = GenesisNeoForgeConfig.SERVER_WORLD_MAP_STRUCTURES.get();
        GenesisConfig.checkForUpdates = GenesisNeoForgeConfig.CHECK_FOR_UPDATES.get();
    }

    private static void applyConfig() {
        GenesisConfig.previewEnabled       = GenesisNeoForgeConfig.PREVIEW_ENABLED.get();
        GenesisConfig.previewThreads       = GenesisNeoForgeConfig.PREVIEW_THREADS.get();
        GenesisConfig.previewInitialRadius = GenesisNeoForgeConfig.PREVIEW_INITIAL_RADIUS.get();
        GenesisConfig.previewExpandRadius  = GenesisNeoForgeConfig.PREVIEW_EXPAND_RADIUS.get();
        GenesisConfig.spawnFromMap         = GenesisNeoForgeConfig.SPAWN_FROM_MAP.get();
        GenesisConfig.showStructures       = GenesisNeoForgeConfig.SHOW_STRUCTURES.get();
        GenesisConfig.pregenDefaultRadius  = GenesisNeoForgeConfig.PREGEN_DEFAULT_RADIUS.get();
        GenesisConfig.pregenDefaultThreads = GenesisNeoForgeConfig.PREGEN_DEFAULT_THREADS.get();
        LOGGER.info("[GenesisNeoForge] Config applied: previewEnabled={} previewThreads={} previewInitial={} previewExpand={} pregenRadius={} pregenThreads={}",
                GenesisConfig.previewEnabled, GenesisConfig.previewThreads,
                GenesisConfig.previewInitialRadius, GenesisConfig.previewExpandRadius,
                GenesisConfig.pregenDefaultRadius, GenesisConfig.pregenDefaultThreads);
    }
}
