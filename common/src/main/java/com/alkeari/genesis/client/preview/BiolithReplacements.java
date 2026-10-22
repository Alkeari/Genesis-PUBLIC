package com.alkeari.genesis.client.preview;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Biolith's biome replacements (Traverse places every biome this way) for a world that does not exist yet.
 * Biolith decides them at sample time from a noise and ranges it builds only when a server starts, so this
 * rebuilds both exactly as that server will: the requests are shuffled with one Random seeded from the world
 * seed, in the order of Biolith's own request map, which is the order the server walks in the same game.
 */
public final class BiolithReplacements {
    private static final String PLACEMENT = "com.terraformersmc.biolith.impl.biome.DimensionBiomePlacement";

    private record Range(ResourceKey<Biome> biome, double end) {}

    private final Object placement;
    private final Method localNoise;
    private final Map<ResourceKey<Biome>, List<Range>> ranges;
    private final Map<ResourceKey<Biome>, Holder<Biome>> holders = new ConcurrentHashMap<>();
    private final Registry<Biome> registry;
    private final ResourceKey<Biome> vanillaPlaceholder;

    private BiolithReplacements(Object placement, Method localNoise, Map<ResourceKey<Biome>, List<Range>> ranges,
                                Registry<Biome> registry, ResourceKey<Biome> vanillaPlaceholder) {
        this.placement = placement;
        this.localNoise = localNoise;
        this.ranges = ranges;
        this.registry = registry;
        this.vanillaPlaceholder = vanillaPlaceholder;
    }

    /** Null when Biolith replaces nothing in the overworld. Throws when its internals differ from those this reads. */
    @SuppressWarnings("unchecked")
    public static BiolithReplacements of(Object overworld, long seed, Registry<Biome> registry) throws ReflectiveOperationException {
        Class<?> base = Class.forName(PLACEMENT);
        Map<ResourceKey<Biome>, Object> sets = (Map<ResourceKey<Biome>, Object>) accessible(base.getDeclaredField("replacementRequests")).get(overworld);
        if (sets.isEmpty()) return null;
        ResourceKey<Biome> placeholder = (ResourceKey<Biome>) base.getField("VANILLA_PLACEHOLDER").get(null);

        Random random = new Random(seed);
        Map<ResourceKey<Biome>, List<Range>> ranges = new HashMap<>();
        for (Map.Entry<ResourceKey<Biome>, Object> set : sets.entrySet()) {
            List<Object> requests = new ArrayList<>((List<Object>) accessible(set.getValue().getClass().getDeclaredField("requests")).get(set.getValue()));
            Class<?> requestClass = Class.forName(PLACEMENT + "$ReplacementRequest");
            Method biomeOf = accessible(requestClass.getDeclaredMethod("biome"));
            Method rateOf = accessible(requestClass.getDeclaredMethod("rate"));

            List<ResourceKey<Biome>> biomes = new ArrayList<>();
            List<Double> rates = new ArrayList<>();
            double locus = 0.0;
            double maxRate = 0.0;
            for (Object request : requests) {
                ResourceKey<Biome> biome = (ResourceKey<Biome>) biomeOf.invoke(request);
                if (biome.equals(placeholder)) continue;
                double rate = (double) rateOf.invoke(request);
                biomes.add(biome);
                rates.add(rate);
                locus += rate;
                maxRate = Math.max(maxRate, rate);
            }
            double vanilla = Math.max(0.0, Math.min(1.0, 1.0 - maxRate));
            double scale = locus + vanilla;
            if (vanilla > 0.0) {
                biomes.add(placeholder);
                rates.add(vanilla);
            }
            // Shuffled as pairs, so one shuffle of an index list stands for Biolith's shuffle of its request list.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < biomes.size(); i++) order.add(i);
            Collections.shuffle(order, random);
            // A new world's saved order is the shuffle with each biome kept at its first place, so a biome named
            // twice moves its requests together, as Biolith's stable sort by that order does.
            List<ResourceKey<Biome>> firstPlaces = new ArrayList<>();
            for (int i : order) if (!firstPlaces.contains(biomes.get(i))) firstPlaces.add(biomes.get(i));
            order.sort(Comparator.comparingInt(i -> firstPlaces.indexOf(biomes.get(i))));
            List<Range> setRanges = new ArrayList<>();
            double end = 0.0;
            for (int i : order) {
                end += rates.get(i) / scale;
                setRanges.add(new Range(biomes.get(i), end));
            }
            ranges.put(set.getKey(), setRanges);
        }

        // A fresh placement of Biolith's own overworld class supplies its noise scales; only its noise is seeded here.
        Object placement = accessible(overworld.getClass().getDeclaredConstructor()).newInstance();
        Constructor<?> noise = Class.forName("com.terraformersmc.biolith.impl.noise.OpenSimplexNoise2").getConstructor(long.class);
        accessible(base.getDeclaredField("replacementNoise")).set(placement, noise.newInstance(seed));
        int[] seedlets = (int[]) accessible(base.getDeclaredField("seedlets")).get(placement);
        for (int i = 0; i < 8; i++) seedlets[i] = (int) (seed >> i * 8 & 255L);
        Method localNoise = accessible(overworld.getClass().getMethod("getLocalNoise", int.class, int.class, int.class));
        return new BiolithReplacements(placement, localNoise, ranges, registry, placeholder);
    }

    /** The biome Biolith puts at these quart coordinates in place of the one the climate chose. */
    public Holder<Biome> apply(int quartX, int quartY, int quartZ, Holder<Biome> chosen) {
        Optional<ResourceKey<Biome>> key = chosen.unwrapKey();
        if (key.isEmpty()) return chosen;
        List<Range> set = ranges.get(key.get());
        if (set == null) return chosen;
        double noise;
        try {
            noise = (double) localNoise.invoke(placement, quartX, quartY, quartZ);
        } catch (ReflectiveOperationException e) {
            return chosen;
        }
        for (Range range : set) {
            if (range.end() >= noise) {
                if (range.biome().equals(vanillaPlaceholder)) return chosen;
                Holder<Biome> replacement = holders.get(range.biome());
                if (replacement == null) {
                    Optional<? extends Holder<Biome>> found = registry.getHolder(range.biome());
                    if (found.isEmpty()) return chosen;
                    replacement = found.get();
                    holders.put(range.biome(), replacement);
                }
                return replacement;
            }
        }
        return chosen;
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T member) {
        member.setAccessible(true);
        return member;
    }
}
