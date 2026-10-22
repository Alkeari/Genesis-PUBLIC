package com.alkeari.genesis.map;

import com.alkeari.genesis.config.GenesisConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Samples the biomes of a server's world for players' world maps, since a client connected to a server
 * has neither the seed nor the generator. Runs on its own thread so the server tick is never held up.
 */
public final class WorldMapService {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/WorldMapService");

    /** Chunks per side of one requested region. */
    public static final int REGION = 32;

    /** Regions one player may have waiting at once, so a fast pan cannot queue unbounded work. */
    public static final int MAX_PENDING = 8;

    // Block Y 320 in quart coordinates, above all terrain, matching the creation-screen preview.
    private static final int QUART_Y = 80;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "genesis-world-map");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    private static final Map<UUID, AtomicInteger> PENDING = new ConcurrentHashMap<>();

    /** One sampled region: a palette of biome ids and, per chunk in row order, its palette index or -1. */
    public record Region(ResourceLocation dimension, int minChunkX, int minChunkZ,
                         List<ResourceLocation> palette, short[] indices, List<StructureLocator.Marker> structures) {}

    // One locator per level, so stronghold rings are worked out once and checked regions are remembered.
    private static final Map<ServerLevel, StructureLocator> LOCATORS = new java.util.WeakHashMap<>();

    private WorldMapService() {}

    public static boolean allowed() {
        return GenesisConfig.serverWorldMap;
    }

    public static void request(ServerPlayer player, int minChunkX, int minChunkZ, Consumer<Region> reply) {
        if (!allowed()) return;
        AtomicInteger pending = PENDING.computeIfAbsent(player.getUUID(), id -> new AtomicInteger());
        if (pending.incrementAndGet() > MAX_PENDING) {
            pending.decrementAndGet();
            return;
        }
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        StructureLocator locator = GenesisConfig.serverWorldMapStructures
                ? LOCATORS.computeIfAbsent(level, l -> new StructureLocator(l.getChunkSource().getGeneratorState(),
                        source, sampler, () -> 0, () -> 0, () -> 0, () -> 0, StructureLocator.exact(l)))
                : null;
        EXECUTOR.execute(() -> {
            ResourceLocation dimension = level.dimension().location();
            Region region;
            try {
                region = sample(dimension, source, sampler, minChunkX, minChunkZ);
                if (locator != null) {
                    region = new Region(dimension, minChunkX, minChunkZ, region.palette(), region.indices(),
                            locator.scan(minChunkX, minChunkX + REGION - 1, minChunkZ, minChunkZ + REGION - 1));
                }
            } catch (Throwable t) {
                LOGGER.warn("[WorldMapService] Sampling region ({}, {}) failed", minChunkX, minChunkZ, t);
                // Answered anyway, as unknown tiles, since the client counts its requests in flight.
                short[] unknown = new short[REGION * REGION];
                java.util.Arrays.fill(unknown, (short) -1);
                region = new Region(dimension, minChunkX, minChunkZ, List.of(), unknown, List.of());
            }
            Region done = region;
            server.execute(() -> {
                pending.decrementAndGet();
                if (!player.hasDisconnected()) reply.accept(done);
            });
        });
    }

    private static Region sample(ResourceLocation dimension, BiomeSource source, Climate.Sampler sampler,
                                 int minChunkX, int minChunkZ) {
        List<ResourceLocation> palette = new ArrayList<>();
        Map<ResourceLocation, Short> paletteIndex = new HashMap<>();
        short[] indices = new short[REGION * REGION];
        for (int dz = 0; dz < REGION; dz++) {
            for (int dx = 0; dx < REGION; dx++) {
                Holder<Biome> holder = source.getNoiseBiome((minChunkX + dx) * 4 + 2, QUART_Y, (minChunkZ + dz) * 4 + 2, sampler);
                ResourceLocation id = holder.unwrapKey().map(key -> key.location()).orElse(null);
                short index = -1;
                if (id != null) {
                    index = paletteIndex.computeIfAbsent(id, key -> {
                        palette.add(key);
                        return (short) (palette.size() - 1);
                    });
                }
                indices[dz * REGION + dx] = index;
            }
        }
        return new Region(dimension, minChunkX, minChunkZ, palette, indices, List.of());
    }
}
