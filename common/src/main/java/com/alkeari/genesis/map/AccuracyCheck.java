package com.alkeari.genesis.map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Measures how closely the maps match the world the game generated: every chunk in a square is generated, then
 * the biome the maps draw for it is compared with the biomes the chunk holds, and the structure markers with the
 * structure starts the chunks hold. Present only when the JVM runs with {@code -Dgenesis.verify=true}.
 */
public final class AccuracyCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/AccuracyCheck");

    public static final boolean ENABLED = Boolean.getBoolean("genesis.verify");

    private static final int MAP_QUART_Y = 80;
    private static final int CHUNKS_PER_TICK = 16;

    private static AccuracyCheck running;

    private final CommandSourceStack source;
    private final ServerLevel level;
    private final int minX, maxX, minZ, maxZ;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private int nextIndex = 0;

    private int chunks = 0;
    private int matchStoredAtMapHeight = 0;
    private int matchStoredAtSurface = 0;
    private int matchAtBlock = 0;
    private int matchChunkMajority = 0;
    private final Map<String, Integer> surfaceMismatches = new HashMap<>();
    private final Set<String> actualStarts = new HashSet<>();
    // From -Dgenesis.verify.preview: the creation screen's biome per chunk, written when that screen closed.
    private final Map<String, String> previewed = readPreview();
    private int previewCompared = 0;
    private int previewMatches = 0;
    private final Map<String, Integer> previewMismatches = new HashMap<>();

    // Every step-th chunk on each axis is measured, so a wide area costs a fraction of generating all of it.
    private final int step;

    private AccuracyCheck(CommandSourceStack source, ServerLevel level, int centerChunkX, int centerChunkZ, int radius, int step) {
        this.source = source;
        this.step = step;
        this.level = level;
        this.minX = centerChunkX - radius;
        this.maxX = centerChunkX + radius;
        this.minZ = centerChunkZ - radius;
        this.maxZ = centerChunkZ + radius;
        this.biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        this.sampler = level.getChunkSource().randomState().sampler();
    }

    public static boolean start(CommandSourceStack source, ServerLevel level, int centerChunkX, int centerChunkZ, int radius, int step) {
        if (running != null) return false;
        running = new AccuracyCheck(source, level, centerChunkX, centerChunkZ, radius, step);
        int perSide = 2 * radius / step + 1;
        LOGGER.info("[Genesis verify] Start: {} chunks {}..{} x {}..{}, every {} chunk(s), in {}", perSide * perSide,
                running.minX, running.maxX, running.minZ, running.maxZ, step, level.dimension().location());
        return true;
    }

    public static void onServerTick(MinecraftServer server) {
        AccuracyCheck check = running;
        if (check == null) return;
        try {
            if (check.step()) {
                running = null;
                check.finish();
            }
        } catch (Throwable t) {
            running = null;
            LOGGER.error("[Genesis verify] Failed", t);
            check.source.sendFailure(Component.literal("[Genesis] Accuracy check failed: " + t));
        }
    }

    private boolean step() {
        int width = (maxX - minX) / step + 1;
        int total = width * ((maxZ - minZ) / step + 1);
        for (int n = 0; n < CHUNKS_PER_TICK && nextIndex < total; n++, nextIndex++) {
            measure(minX + nextIndex % width * step, minZ + nextIndex / width * step);
        }
        return nextIndex >= total;
    }

    private void measure(int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
        chunks++;
        ResourceLocation drawn = id(biomeSource.getNoiseBiome(chunkX * 4 + 2, MAP_QUART_Y, chunkZ * 4 + 2, sampler));

        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        ResourceLocation storedAtMapHeight = id(chunk.getNoiseBiome(chunkX * 4 + 2, MAP_QUART_Y, chunkZ * 4 + 2));
        ResourceLocation storedAtSurface = id(chunk.getNoiseBiome(chunkX * 4 + 2, QuartPos.fromBlock(surfaceY), chunkZ * 4 + 2));
        ResourceLocation atBlock = id(level.getBiome(new BlockPos(chunkX * 16 + 8, surfaceY, chunkZ * 16 + 8)));

        Map<ResourceLocation, Integer> votes = new HashMap<>();
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, qx * 4, qz * 4);
                votes.merge(id(chunk.getNoiseBiome(chunkX * 4 + qx, QuartPos.fromBlock(y), chunkZ * 4 + qz)), 1, Integer::sum);
            }
        }
        ResourceLocation majority = votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        if (same(drawn, storedAtMapHeight)) matchStoredAtMapHeight++;
        if (same(drawn, storedAtSurface)) matchStoredAtSurface++;
        else surfaceMismatches.merge(drawn + " drawn, " + storedAtSurface + " generated", 1, Integer::sum);
        if (same(drawn, atBlock)) matchAtBlock++;
        if (same(drawn, majority)) matchChunkMajority++;
        String preview = previewed.get(chunkX + " " + chunkZ);
        if (preview != null) {
            previewCompared++;
            if (storedAtSurface != null && preview.equals(storedAtSurface.toString())) previewMatches++;
            else previewMismatches.merge(preview + " previewed, " + storedAtSurface + " generated", 1, Integer::sum);
        }

        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            if (!entry.getValue().isValid()) continue;
            ResourceLocation id = structures.getKey(entry.getKey());
            actualStarts.add(chunkX + "," + chunkZ + " " + id);
        }
    }

    private void finish() throws InterruptedException {
        report("Biome drawn vs generated at Y 320", matchStoredAtMapHeight);
        report("Biome drawn vs generated at the surface", matchStoredAtSurface);
        report("Biome drawn vs F3 at the chunk center", matchAtBlock);
        report("Biome drawn vs the chunk's most common surface biome", matchChunkMajority);
        surfaceMismatches.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(15)
                .forEach(e -> LOGGER.info("[Genesis verify]   {} chunks: {}", e.getValue(), e.getKey()));

        if (!previewed.isEmpty()) {
            LOGGER.info("[Genesis verify] Creation screen preview vs generated at the surface: {} of {} chunks match ({})",
                    previewMatches, previewCompared, String.format("%.2f%%", 100.0 * previewMatches / Math.max(1, previewCompared)));
            previewMismatches.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(15)
                    .forEach(e -> LOGGER.info("[Genesis verify]   {} chunks: {}", e.getValue(), e.getKey()));
        }

        var state = level.getChunkSource().getGeneratorState();
        compare("Structure markers, biome at Y 64", new StructureLocator(state, biomeSource, sampler,
                () -> 0, () -> 0, () -> 0, () -> 0).scan(minX, maxX, minZ, maxZ), null);
        StructureLocator.StartCheck exact = StructureLocator.exact(level);
        compare("Structure markers, game's own check", new StructureLocator(state, biomeSource, sampler,
                () -> 0, () -> 0, () -> 0, () -> 0, exact).scan(minX, maxX, minZ, maxZ), exact);
        source.sendSuccess(() -> Component.literal("[Genesis] Accuracy check done: " + chunks + " chunks, see the log"), false);
    }

    private void report(String what, int matches) {
        LOGGER.info("[Genesis verify] {}: {} of {} chunks match ({})", what, matches, chunks,
                String.format("%.2f%%", 100.0 * matches / Math.max(1, chunks)));
    }

    private void compare(String what, List<StructureLocator.Marker> markers, StructureLocator.StartCheck check) {
        Set<String> marked = markers.stream()
                .filter(m -> Math.floorMod(m.chunkX() - minX, step) == 0 && Math.floorMod(m.chunkZ() - minZ, step) == 0)
                .map(m -> m.chunkX() + "," + m.chunkZ() + " " + m.structure()).collect(Collectors.toSet());
        Set<String> wrong = new HashSet<>(marked);
        wrong.removeAll(actualStarts);
        Set<String> missed = new HashSet<>(actualStarts);
        missed.removeAll(marked);
        LOGGER.info("[Genesis verify] {}: {} marked, {} generated, {} marked and generated, {} marked but not generated, {} generated but not marked",
                what, marked.size(), actualStarts.size(), marked.size() - wrong.size(), wrong.size(), missed.size());
        wrong.stream().sorted().limit(15).forEach(s -> LOGGER.info("[Genesis verify]   marked, not generated: {}", s));
        missed.stream().sorted().limit(15).forEach(s -> {
            LOGGER.info("[Genesis verify]   generated, not marked: {}", s);
            if (check != null) explainMissed(s, check);
        });
    }

    // Walks the steps the locator takes for a start it did not mark, to show which one disagrees with the game.
    private void explainMissed(String start, StructureLocator.StartCheck check) {
        String[] parts = start.split(" ");
        String[] xz = parts[0].split(",");
        int chunkX = Integer.parseInt(xz[0]);
        int chunkZ = Integer.parseInt(xz[1]);
        var state = level.getChunkSource().getGeneratorState();
        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        boolean found = false;
        for (int i = 0; i < sets.size(); i++) {
            StructureSet set = sets.get(i).value();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                if (!entry.structure().unwrapKey().map(k -> k.location().toString().equals(parts[1])).orElse(false)) continue;
                found = true;
                StructurePlacement placement = set.placement();
                String setId = sets.get(i).unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
                String where;
                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    int rx = Math.floorDiv(chunkX, spread.spacing());
                    int rz = Math.floorDiv(chunkZ, spread.spacing());
                    ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(), chunkX, chunkZ);
                    where = "spread, spacing " + spread.spacing() + ", region " + rx + "," + rz + " picks chunk " + potential.x + "," + potential.z;
                } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                    List<ChunkPos> ring = state.getRingPositionsFor(rings);
                    where = "rings, chunk in ring positions " + (ring != null && ring.contains(new ChunkPos(chunkX, chunkZ)));
                } else {
                    where = placement.getClass().getName();
                }
                LOGGER.info("[Genesis verify]     set {} (index {}, {} entries): {}; placement says structure chunk {}; game's check here {}",
                        setId, i, set.structures().size(), where, placement.isStructureChunk(state, chunkX, chunkZ),
                        check.starts(entry.structure(), chunkX, chunkZ));
            }
        }
        if (!found) LOGGER.info("[Genesis verify]     no structure set the generator offers holds {}", parts[1]);
    }

    private static Map<String, String> readPreview() {
        String path = System.getProperty("genesis.verify.preview");
        Map<String, String> result = new HashMap<>();
        if (path == null) return result;
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(path))) {
                int last = line.lastIndexOf(' ');
                if (last > 0) result.put(line.substring(0, last), line.substring(last + 1));
            }
            LOGGER.info("[Genesis verify] Read {} previewed chunks from {}", result.size(), path);
        } catch (Exception e) {
            LOGGER.warn("[Genesis verify] Could not read {}", path, e);
        }
        return result;
    }

    private static ResourceLocation id(Holder<Biome> holder) {
        return holder.unwrapKey().map(key -> key.location()).orElse(null);
    }

    private static boolean same(ResourceLocation a, ResourceLocation b) {
        return a != null && a.equals(b);
    }
}
