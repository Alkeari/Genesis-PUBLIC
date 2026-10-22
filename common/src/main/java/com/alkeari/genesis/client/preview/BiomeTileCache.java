package com.alkeari.genesis.client.preview;

import com.alkeari.genesis.client.GenesisTheme;

import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeTileCache {

    private final ConcurrentHashMap<Long, Integer> tiles   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> borders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String>  names   = new ConcurrentHashMap<>();
    // Translation key saying why the map is not exactly what the world generates, or null when it is.
    private volatile String approximateNote;

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void put(int chunkX, int chunkZ, int fillArgb, int borderArgb, String name) {
        long k = key(chunkX, chunkZ);
        tiles.put(k, fillArgb);
        borders.put(k, borderArgb);
        names.put(k, name);
    }

    public int get(int chunkX, int chunkZ) {
        return tiles.getOrDefault(key(chunkX, chunkZ), GenesisTheme.UNSAMPLED_TILE);
    }

    public int getBorderColor(int chunkX, int chunkZ) {
        return borders.getOrDefault(key(chunkX, chunkZ), GenesisTheme.UNSAMPLED_TILE);
    }

    public String getName(int chunkX, int chunkZ) {
        return names.getOrDefault(key(chunkX, chunkZ), "");
    }

    /** The biome id drawn for a chunk ({@code namespace:path}), or null when the tile has no biome. */
    public String getBiomeId(int chunkX, int chunkZ) {
        String name = names.get(key(chunkX, chunkZ));
        if (name == null || !name.startsWith("biome.")) return null;
        String id = name.substring("biome.".length());
        int dot = id.indexOf('.');
        return dot < 0 ? null : id.substring(0, dot) + ":" + id.substring(dot + 1);
    }

    /** Records that the map is approximate; the first reason given is the one shown. */
    public void markApproximate(String translationKey) {
        if (approximateNote == null) approximateNote = translationKey;
    }

    public String approximateNote() {
        return approximateNote;
    }

    public boolean has(int chunkX, int chunkZ) {
        return tiles.containsKey(key(chunkX, chunkZ));
    }

    /**
     * Returns the unique biomes visible in the given chunk range, in first-seen order.
     * Keys are translation keys (e.g. "biome.minecraft.plains"); values are ARGB colors.
     * Skips unsampled and unnamed tiles.
     */
    public LinkedHashMap<String, Integer> getVisibleBiomes(
            int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                long k = key(cx, cz);
                Integer color = tiles.get(k);
                String  name  = names.get(k);
                if (color == null || name == null || name.isEmpty()
                        || color == GenesisTheme.UNSAMPLED_TILE) continue;
                result.putIfAbsent(name, color);
            }
        }
        return result;
    }

    public void clear() {
        tiles.clear();
        borders.clear();
        names.clear();
        approximateNote = null;
    }
}
