package com.alkeari.genesis.fabric;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.genesis.config.fabric.GenesisFabricConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class GenesisFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisFabric");

    @Override
    public void onInitialize() {
        LOGGER.info("[GenesisFabric] Initializing Genesis (Fabric)");
        AutoConfig.register(GenesisFabricConfig.class, GsonConfigSerializer::new);
        GenesisFabricConfig cfg = AutoConfig.getConfigHolder(GenesisFabricConfig.class).getConfig();
        GenesisConfig.previewEnabled       = cfg.preview_enabled;
        GenesisConfig.previewThreads       = clamp(cfg.preview_threads, 0, 16);
        GenesisConfig.previewInitialRadius = clamp(cfg.preview_initial_radius, 16, 512);
        GenesisConfig.previewExpandRadius  = clamp(cfg.preview_expand_radius, 4, 128);
        GenesisConfig.spawnFromMap         = cfg.spawn_from_map;
        GenesisConfig.showStructures       = cfg.show_structures;
        GenesisConfig.serverWorldMap       = cfg.server_world_map;
        GenesisConfig.serverWorldMapStructures = cfg.server_world_map_structures;
        GenesisConfig.checkForUpdates      = cfg.check_for_updates;
        GenesisConfig.pregenDefaultRadius  = clamp(cfg.pregen_default_radius, 1, 2048);
        GenesisConfig.pregenDefaultThreads = clamp(cfg.pregen_default_threads, 1, 16);
        LOGGER.info("[GenesisFabric] Config applied - previewEnabled={} previewThreads={} previewInitial={} previewExpand={} pregenRadius={} pregenThreads={}",
                GenesisConfig.previewEnabled, GenesisConfig.previewThreads,
                GenesisConfig.previewInitialRadius, GenesisConfig.previewExpandRadius,
                GenesisConfig.pregenDefaultRadius, GenesisConfig.pregenDefaultThreads);
        Genesis.init();
    }

    // The Fabric file is plain JSON, so the ranges Forge and NeoForge enforce are applied here.
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
