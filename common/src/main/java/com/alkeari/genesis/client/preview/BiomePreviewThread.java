package com.alkeari.genesis.client.preview;

import com.mojang.datafixers.util.Pair;
import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.config.GenesisConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

public final class BiomePreviewThread extends Thread {

    private volatile boolean stopped = false;

    @Override
    public void interrupt() {
        stopped = true;
        super.interrupt();
    }

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BiomePreviewThread.class);

    // Block Y 320 converted to quart coordinates (>> 2).
    // Above all terrain, so we never sample a cave biome. The horizontal
    // climate parameters (temperature, humidity, etc.) are unaffected by Y,
    // so biome distribution is correct. getBaseHeight() is not called at all.
    private static final int FIXED_QUART_Y = 80;

    // Cached reflection handle for GeoGradient's bridge lookup (null = not present or not available).
    // When GeoGradient + TerraBlender are both active, this lets the preview bypass TerraBlender's
    // per-region RTrees and use the latitude-aware combined bridge list directly.
    private static volatile boolean   ggReflectionChecked  = false;
    private static volatile Method    ggFindBiomeForPreview = null;

    // One-shot flag so we emit at most one WARN per session when sampling fails.
    private static final AtomicBoolean LOGGED_SAMPLING_ERROR = new AtomicBoolean(false);

    // Snapshot config values at thread creation so a live config reload mid-run
    // doesn't change the spiral size partway through.
    private final int initialRadius;
    private final int expandRadius;

    private final BiomeSource     biomeSource;
    private final Climate.Sampler sampler;
    private final BiomeTileCache  cache;
    private final int             threadIndex;
    private final int             numThreads;
    private final IntSupplier     viewMinChunkX;
    private final IntSupplier     viewMaxChunkX;
    private final IntSupplier     viewMinChunkZ;
    private final IntSupplier     viewMaxChunkZ;

    // Reverse identity map: biome instance -> ResourceLocation.
    // Used to resolve Holder.Direct (unregistered/wrapped) biomes returned by some mods.
    private final IdentityHashMap<Biome, ResourceLocation> biomeByValue;

    // TerraBlender fallback: vanilla parameter list built from all registered regions.
    // Non-null when TerraBlender is installed and we successfully collected its params.
    // If the live biomeSource throws (e.g. ClassCastException due to TBSampler mismatch),
    // we fall back to a linear fitness-based lookup over this list using a plain vanilla sampler.
    private final List<Pair<Climate.ParameterPoint, Holder<Biome>>> tbParams;

    // Where sampling spirals out from, in chunks: the world origin on the creation screen, the player in game.
    private int originChunkX = 0;
    private int originChunkZ = 0;

    // A running world's biome source already carries every mod's changes, so the GeoGradient preview bridge is skipped.
    private boolean liveWorld = false;
    private BiolithReplacements biolith;

    public BiomePreviewThread(
            BiomeSource biomeSource,
            Climate.Sampler sampler,
            BiomeTileCache cache,
            int threadIndex,
            int numThreads,
            Registry<Biome> biomeRegistry,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> tbParams,
            IntSupplier viewMinChunkX,
            IntSupplier viewMaxChunkX,
            IntSupplier viewMinChunkZ,
            IntSupplier viewMaxChunkZ
    ) {
        super("genesis-biome-preview-" + threadIndex);
        this.biomeSource   = biomeSource;
        this.sampler       = sampler;
        this.cache         = cache;
        this.threadIndex   = threadIndex;
        this.numThreads    = numThreads;
        this.tbParams      = tbParams;
        this.viewMinChunkX = viewMinChunkX;
        this.viewMaxChunkX = viewMaxChunkX;
        this.viewMinChunkZ = viewMinChunkZ;
        this.viewMaxChunkZ = viewMaxChunkZ;
        this.initialRadius = GenesisConfig.previewInitialRadius;
        this.expandRadius  = GenesisConfig.previewExpandRadius;

        // Build the reverse identity map from the biome registry so we can resolve
        // Holder.Direct instances (used by some mod compatibility layers like Corgilib).
        IdentityHashMap<Biome, ResourceLocation> reverseMap = new IdentityHashMap<>();
        if (biomeRegistry != null) {
            for (Biome biome : biomeRegistry) {
                ResourceLocation key = biomeRegistry.getKey(biome);
                if (key != null) reverseMap.put(biome, key);
            }
        }
        this.biomeByValue = reverseMap;

        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY + 1);
    }

    /** Samples a running world from the given chunk outward; call before {@link #start()}. */
    public BiomePreviewThread forLiveWorld(int originChunkX, int originChunkZ) {
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        this.liveWorld    = true;
        return this;
    }

    /** Applies Biolith's replacements to every sample; call before {@link #start()}. */
    public BiomePreviewThread withBiolith(BiolithReplacements biolith) {
        this.biolith = biolith;
        return this;
    }

    @Override
    public void run() {
        try {
            // Three-pass spiral: coarse -> medium -> fine.
            spiralSample(originChunkX, originChunkZ, initialRadius, 4);
            spiralSample(originChunkX, originChunkZ, initialRadius, 2);
            spiralSample(originChunkX, originChunkZ, initialRadius, 1);

            int radius = initialRadius;
            while (!Thread.interrupted()) {
                radius += expandRadius;
                int minX = Math.min(viewMinChunkX.getAsInt(), originChunkX - radius);
                int maxX = Math.max(viewMaxChunkX.getAsInt(), originChunkX + radius);
                int minZ = Math.min(viewMinChunkZ.getAsInt(), originChunkZ - radius);
                int maxZ = Math.max(viewMaxChunkZ.getAsInt(), originChunkZ + radius);
                fillRect(minX, maxX, minZ, maxZ, 1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Biome preview thread {} crashed unexpectedly", threadIndex, t);
        }
    }

    /**
     * Attempts a latitude-aware biome lookup via GeoGradient's TerraBlender bridge.
     * Returns null if GeoGradient is not installed, the bridge is not active, or the
     * lookup fails for any reason - callers must fall back to getNoiseBiome() in that case.
     */
    @SuppressWarnings("unchecked")
    private Holder<Biome> tryGeoGradientBridgeLookup(int quartX, int quartZ) {
        if (!ggReflectionChecked) {
            synchronized (BiomePreviewThread.class) {
                if (!ggReflectionChecked) {
                    try {
                        Class<?> c = Class.forName("com.alkeari.geogradient.worldgen.GeoGradientClimate");
                        ggFindBiomeForPreview = c.getMethod(
                                "findBiomeForPreview", int.class, int.class, int.class, Climate.Sampler.class);
                    } catch (ClassNotFoundException ignored) {
                    } catch (Exception e) {
                        LOGGER.warn("[Genesis] Failed to resolve GeoGradient findBiomeForPreview", e);
                    }
                    ggReflectionChecked = true;
                }
            }
        }
        if (ggFindBiomeForPreview == null) return null;
        try {
            return (Holder<Biome>) ggFindBiomeForPreview.invoke(null, quartX, FIXED_QUART_Y, quartZ, sampler);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void spiralSample(int originX, int originZ, int radius, int step) throws InterruptedException {
        for (int r = 0; r <= radius; r += step) {
            for (int dx = -r; dx <= r; dx += step) {
                sampleIfAbsent(originX + dx, originZ - r);
                sampleIfAbsent(originX + dx, originZ + r);
                if (Thread.interrupted()) throw new InterruptedException();
            }
            for (int dz = -r + 1; dz < r; dz += step) {
                sampleIfAbsent(originX - r, originZ + dz);
                sampleIfAbsent(originX + r, originZ + dz);
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void fillRect(int minX, int maxX, int minZ, int maxZ, int step) throws InterruptedException {
        for (int cz = minZ; cz <= maxZ; cz += step) {
            for (int cx = minX; cx <= maxX; cx += step) {
                sampleIfAbsent(cx, cz);
                if (Thread.interrupted()) throw new InterruptedException();
            }
        }
    }

    private void sampleIfAbsent(int chunkX, int chunkZ) {
        if (Math.floorMod(chunkZ, numThreads) != threadIndex) return;
        if (cache.has(chunkX, chunkZ)) return;

        int quartX = chunkX * 4 + 2;
        int quartZ = chunkZ * 4 + 2;

        Holder<Biome> holder = sampleBiome(quartX, quartZ);
        // A thread told to stop may still be mid-sample after the cache was cleared for a new seed.
        if (stopped) return;
        if (holder == null) {
            cache.put(chunkX, chunkZ, GenesisTheme.STEEL_GRAY, GenesisTheme.HAIRLINE, UNREADABLE);
            return;
        }

        // Resolve the biome's ResourceLocation.
        // Handle both Holder.Reference (normal path) and Holder.Direct (used by mods like Corgilib
        // that wrap biome holders without registering them).
        ResourceLocation loc = null;
        if (holder.unwrapKey().isPresent()) {
            loc = holder.unwrapKey().get().location();
        } else if (!biomeByValue.isEmpty()) {
            loc = biomeByValue.get(holder.value());
        }
        putBiome(cache, chunkX, chunkZ, loc, holder.value());
    }

    /**
     * Colors one map tile from its biome: the category color for a known biome, otherwise one derived from the
     * biome's own grass color or its id. {@code biome} may be null when only the id is known.
     */
    public static void putBiome(BiomeTileCache cache, int chunkX, int chunkZ, ResourceLocation loc, Biome biome) {
        int    fill   = GenesisTheme.STEEL_GRAY;
        int    border = GenesisTheme.HAIRLINE;
        String name   = "";

        if (loc != null) {
            name = "biome." + loc.getNamespace() + "." + loc.getPath();
            int mappedFill = BiomeColorMap.getCategoryColor(loc);
            if (mappedFill != GenesisTheme.STEEL_GRAY) {
                fill   = mappedFill;
                border = BiomeColorMap.getBorderColor(loc);
            } else if (!"minecraft".equals(loc.getNamespace())) {
                fill   = deriveModdedBiomeColor(loc, biome);
                border = fill;
            }
        } else {
            // No key at all: a color derived from the biome's properties, named as unnamed in the legend.
            name = UNNAMED;
            //? if >=1.21 {
            fill   = deriveModdedBiomeColor(ResourceLocation.fromNamespaceAndPath("genesis", "unknown"), biome);
            //?} else
            /*fill   = deriveModdedBiomeColor(new ResourceLocation("genesis", "unknown"), biome);*/
            border = fill;
        }

        cache.put(chunkX, chunkZ, fill, border, name);
    }

    /**
     * Tries to obtain the biome holder for the given quart coordinates. Attempts, in order:
     * 1. GeoGradient bridge (if installed)
     * 2. Live biomeSource.getNoiseBiome() (standard path)
     * 3. TerraBlender parameter-list fallback (when the live source throws, e.g. because
     *    it requires a TBSampler-extended Climate.Sampler that Genesis does not provide)
     *
     * Returns null only if all paths fail.
     */
    private Holder<Biome> sampleBiome(int quartX, int quartZ) {
        Holder<Biome> chosen = sampleClimateBiome(quartX, quartZ);
        return chosen == null || biolith == null ? chosen : biolith.apply(quartX, FIXED_QUART_Y, quartZ, chosen);
    }

    private Holder<Biome> sampleClimateBiome(int quartX, int quartZ) {
        // GeoGradient bridge takes priority when installed.
        Holder<Biome> bridgeResult = liveWorld ? null : tryGeoGradientBridgeLookup(quartX, quartZ);
        if (bridgeResult != null) return bridgeResult;

        // Normal path: ask the live biome source.
        try {
            Holder<Biome> live = biomeSource.getNoiseBiome(quartX, FIXED_QUART_Y, quartZ, sampler);
            // An uninitialized TerraBlender list answers with its placeholder, which no world generates.
            if (!isDeferredPlaceholder(live) || tbParams == null) return live;
        } catch (Throwable t) {
            // One-shot warning so the log isn't spammed with thousands of repeated errors.
            if (LOGGED_SAMPLING_ERROR.compareAndSet(false, true)) {
                LOGGER.warn("[Genesis] Biome sampling failed ({}: {}); switching to TerraBlender"
                        + " parameter-list fallback if available. Enable debug logging for the"
                        + " full stack trace.", t.getClass().getSimpleName(), t.getMessage());
                LOGGER.debug("[Genesis] Full biome sampling error", t);
            }
        }

        // TerraBlender fallback: use the pre-built vanilla parameter list for a linear
        // fitness-based lookup with the plain vanilla sampler.  No TBSampler cast needed.
        if (tbParams != null) {
            if (!stopped) cache.markApproximate("genesis.approximate.fallback");
            return lookupFromTBParams(quartX, quartZ);
        }

        return null;
    }

    /**
     * Finds the best-matching biome from the TerraBlender parameter list.
     * Uses manual 6D distance via public record accessors instead of the package-private
     * Climate.ParameterPoint.fitness() - semantically identical for the 6 standard dimensions.
     */
    private Holder<Biome> lookupFromTBParams(int quartX, int quartZ) {
        try {
            Climate.TargetPoint target = sampler.sample(quartX, FIXED_QUART_Y, quartZ);
            Holder<Biome> best = null;
            long bestDist = Long.MAX_VALUE;
            for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : tbParams) {
                // TerraBlender regions fill the climate they leave to vanilla with this placeholder, which a real
                // world never generates; the vanilla entries in the same list answer those points instead.
                if (isDeferredPlaceholder(pair.getSecond())) continue;
                long dist = paramFitness(pair.getFirst(), target);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pair.getSecond();
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isDeferredPlaceholder(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().toString().equals("terrablender:deferred_placeholder")).orElse(false);
    }

    /** Legend and hover name of a tile whose biome could not be read. */
    public static final String UNREADABLE = "biome.genesis.unreadable";
    /** Legend and hover name of a biome the game gave no id. */
    public static final String UNNAMED = "biome.genesis.unnamed";

    /** Squared 6D climate distance - equivalent to ParameterPoint.fitness() for the standard dimensions. */
    private static long paramFitness(Climate.ParameterPoint p, Climate.TargetPoint t) {
        return rangeDistSq(p.temperature(),     t.temperature())
             + rangeDistSq(p.humidity(),        t.humidity())
             + rangeDistSq(p.continentalness(), t.continentalness())
             + rangeDistSq(p.erosion(),         t.erosion())
             + rangeDistSq(p.depth(),           t.depth())
             + rangeDistSq(p.weirdness(),       t.weirdness());
    }

    /** Squared distance from a value to the nearest point in [param.min, param.max]. */
    private static long rangeDistSq(Climate.Parameter param, long value) {
        long lo = param.min(), hi = param.max();
        long d = value < lo ? lo - value : (value > hi ? value - hi : 0L);
        return d * d;
    }

    private static int deriveModdedBiomeColor(ResourceLocation loc, Biome biome) {
        Optional<Integer> grassOverride = biome == null ? Optional.empty() : biome.getSpecialEffects().getGrassColorOverride();
        if (grassOverride.isPresent()) {
            return 0xFF000000 | grassOverride.get();
        }
        int hash = loc.toString().hashCode();
        float hue = ((hash & 0x7FFFFFFF) % 360) / 360f;
        return hsvToArgb(hue, 0.55f, 0.55f);
    }

    @SuppressWarnings("SameParameterValue")
    private static int hsvToArgb(float h, float s, float v) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0  -> { r = v; g = t; b = p; }
            case 1  -> { r = q; g = v; b = p; }
            case 2  -> { r = p; g = v; b = t; }
            case 3  -> { r = p; g = q; b = v; }
            case 4  -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }
}
