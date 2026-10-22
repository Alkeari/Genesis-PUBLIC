package com.alkeari.genesis.map;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Finds where structures can start, from the seed alone, the way the game picks them: each structure set's
 * placement names its candidate chunks, the set's weighted pick chooses a structure there, and the structure's
 * structure must be able to start there. With a {@link #exact(ServerLevel) live world} that last check is the
 * game's own, terrain and the biome at the start's real height included; without one it is the biome at Y 64
 * alone, so terrain can still stop a marked structure.
 */
public final class StructureLocator extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/StructureLocator");

    // Block Y 64 in quart coordinates: the surface biome most structures are placed in.
    private static final int QUART_Y = 16;

    public record Marker(int chunkX, int chunkZ, ResourceLocation structure) {}

    /** Whether a structure would start in a chunk, once the set's placement has chosen it. */
    @FunctionalInterface
    public interface StartCheck {
        boolean starts(Holder<Structure> structure, int chunkX, int chunkZ);
    }

    /** The game's own start check against a live world: the same call chunk generation makes. */
    public static StartCheck exact(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templates = level.getServer().getStructureManager();
        RegistryAccess registries = level.registryAccess();
        long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        return (structure, chunkX, chunkZ) -> structure.value().generate(registries, generator,
                generator.getBiomeSource(), randomState, templates, seed, new ChunkPos(chunkX, chunkZ), 0, level,
                structure.value().biomes()::contains).isValid();
    }

    /** True when the locator checks starts the game's own way rather than by biome alone. */
    public boolean isExact() {
        return exact;
    }

    private final ChunkGeneratorStructureState state;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final IntSupplier viewMinChunkX;
    private final IntSupplier viewMaxChunkX;
    private final IntSupplier viewMinChunkZ;
    private final IntSupplier viewMaxChunkZ;
    private final StartCheck startCheck;
    private final boolean exact;

    // Keyed by the marker itself, since structures of different sets can start in the same chunk.
    private final Set<Marker> markers = ConcurrentHashMap.newKeySet();
    private final Set<Long> checkedRegions = ConcurrentHashMap.newKeySet();
    private boolean ringsLocated = false;
    private volatile boolean pickFailureLogged = false;

    public StructureLocator(ChunkGeneratorStructureState state, BiomeSource biomeSource, Climate.Sampler sampler,
                            IntSupplier viewMinChunkX, IntSupplier viewMaxChunkX,
                            IntSupplier viewMinChunkZ, IntSupplier viewMaxChunkZ) {
        this(state, biomeSource, sampler, viewMinChunkX, viewMaxChunkX, viewMinChunkZ, viewMaxChunkZ, null);
    }

    /** With a non-null check, starts are confirmed by it instead of by the biome at Y 64. */
    public StructureLocator(ChunkGeneratorStructureState state, BiomeSource biomeSource, Climate.Sampler sampler,
                            IntSupplier viewMinChunkX, IntSupplier viewMaxChunkX,
                            IntSupplier viewMinChunkZ, IntSupplier viewMaxChunkZ, StartCheck startCheck) {
        super("genesis-structure-locator");
        this.exact         = startCheck != null;
        this.startCheck    = startCheck != null ? startCheck : biomeAtSurface(biomeSource, sampler);
        this.state         = state;
        this.biomeSource   = biomeSource;
        this.sampler       = sampler;
        this.viewMinChunkX = viewMinChunkX;
        this.viewMaxChunkX = viewMaxChunkX;
        this.viewMinChunkZ = viewMinChunkZ;
        this.viewMaxChunkZ = viewMaxChunkZ;
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY + 1);
    }

    public Collection<Marker> markers() {
        return markers;
    }

    /**
     * Locates the structures that start inside the given chunk bounds, without the thread: the server answers
     * world map regions this way, keeping one locator per level so ring positions are worked out once.
     */
    public synchronized List<Marker> scan(int minX, int maxX, int minZ, int maxZ) throws InterruptedException {
        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        if (!ringsLocated) {
            locateRings(sets);
            ringsLocated = true;
        }
        for (int i = 0; i < sets.size(); i++) {
            if (sets.get(i).value().placement() instanceof RandomSpreadStructurePlacement spread) {
                locateSpread(i, sets.get(i).value(), spread, minX, maxX, minZ, maxZ);
            }
        }
        List<Marker> inside = new ArrayList<>();
        for (Marker marker : markers) {
            if (marker.chunkX() >= minX && marker.chunkX() <= maxX && marker.chunkZ() >= minZ && marker.chunkZ() <= maxZ) {
                inside.add(marker);
            }
        }
        return inside;
    }

    @Override
    public void run() {
        try {
            List<Holder<StructureSet>> sets = state.possibleStructureSets();
            locateRings(sets);
            ringsLocated = true;
            while (!Thread.interrupted()) {
                int minX = viewMinChunkX.getAsInt();
                int maxX = viewMaxChunkX.getAsInt();
                int minZ = viewMinChunkZ.getAsInt();
                int maxZ = viewMaxChunkZ.getAsInt();
                for (int i = 0; i < sets.size(); i++) {
                    if (sets.get(i).value().placement() instanceof RandomSpreadStructurePlacement spread) {
                        locateSpread(i, sets.get(i).value(), spread, minX, maxX, minZ, maxZ);
                    }
                }
                Thread.sleep(250);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Structure locator stopped", t);
        }
    }

    /** Strongholds and other ring placements: the game computes every position at once. */
    private void locateRings(List<Holder<StructureSet>> sets) {
        for (Holder<StructureSet> set : sets) {
            if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement rings)) continue;
            List<ChunkPos> positions = state.getRingPositionsFor(rings);
            if (positions == null) continue;
            for (ChunkPos pos : positions) {
                ResourceLocation id = pickOrSkip(set.value(), pos.x, pos.z);
                if (id != null) markers.add(new Marker(pos.x, pos.z, id));
            }
        }
    }

    private void locateSpread(int setIndex, StructureSet set, RandomSpreadStructurePlacement spread,
                              int minX, int maxX, int minZ, int maxZ) throws InterruptedException {
        int spacing = spread.spacing();
        for (int rz = Math.floorDiv(minZ, spacing); rz <= Math.floorDiv(maxZ, spacing); rz++) {
            for (int rx = Math.floorDiv(minX, spacing); rx <= Math.floorDiv(maxX, spacing); rx++) {
                long key = ((long) setIndex << 48) ^ ((long) (rx & 0xFFFFFF) << 24) ^ (rz & 0xFFFFFF);
                if (!checkedRegions.add(key)) continue;
                // Takes any chunk of the region, not the region index: it divides by the spacing itself.
                ChunkPos pos = spread.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
                if (!((StructurePlacement) spread).isStructureChunk(state, pos.x, pos.z)) continue;
                ResourceLocation id = pickOrSkip(set, pos.x, pos.z);
                if (id != null) markers.add(new Marker(pos.x, pos.z, id));
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }
    }

    private static StartCheck biomeAtSurface(BiomeSource biomeSource, Climate.Sampler sampler) {
        return (structure, chunkX, chunkZ) -> structure.value().biomes()
                .contains(biomeSource.getNoiseBiome(chunkX * 4 + 2, QUART_Y, chunkZ * 4 + 2, sampler));
    }

    // A structure a mod builds in a way the check cannot follow costs that one marker, not the whole locator.
    private ResourceLocation pickOrSkip(StructureSet set, int chunkX, int chunkZ) {
        try {
            return pick(set, chunkX, chunkZ);
        } catch (Throwable t) {
            if (!pickFailureLogged) {
                pickFailureLogged = true;
                LOGGER.warn("[Genesis] Could not check a structure start at chunk {}, {}; such starts are left unmarked", chunkX, chunkZ, t);
            }
            return null;
        }
    }

    /** The set's weighted pick, seeded as the game seeds it, keeping the first that can start in the chunk. */
    private ResourceLocation pick(StructureSet set, int chunkX, int chunkZ) {
        List<StructureSet.StructureSelectionEntry> entries = new ArrayList<>(set.structures());
        if (entries.size() == 1) return idIfStarts(entries.get(0), chunkX, chunkZ);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(state.getLevelSeed(), chunkX, chunkZ);
        int total = 0;
        for (StructureSet.StructureSelectionEntry entry : entries) total += entry.weight();
        while (!entries.isEmpty()) {
            int roll = random.nextInt(total);
            StructureSet.StructureSelectionEntry chosen = entries.get(entries.size() - 1);
            for (StructureSet.StructureSelectionEntry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) { chosen = entry; break; }
            }
            ResourceLocation id = idIfStarts(chosen, chunkX, chunkZ);
            if (id != null) return id;
            entries.remove(chosen);
            total -= chosen.weight();
        }
        return null;
    }

    private ResourceLocation idIfStarts(StructureSet.StructureSelectionEntry entry, int chunkX, int chunkZ) {
        if (!startCheck.starts(entry.structure(), chunkX, chunkZ)) return null;
        return entry.structure().unwrapKey().map(key -> key.location()).orElse(null);
    }
}
