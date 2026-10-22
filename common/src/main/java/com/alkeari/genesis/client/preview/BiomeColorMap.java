package com.alkeari.genesis.client.preview;

import com.alkeari.genesis.client.GenesisTheme;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Two-layer color system for the biome map.
 * <p>
 * CATEGORY  - shared fill color for all biomes in the same family.
 *             This is what gets written into BiomeTileCache and painted on the map.
 * <p>
 * BORDER    - unique 1-px outline color per distinct biome, used only in the
 *             legend swatch so siblings within a family are visually distinct.
 */
public final class BiomeColorMap {

    private static final Map<ResourceLocation, Integer> CATEGORY = new HashMap<>();
    private static final Map<ResourceLocation, Integer> BORDER   = new HashMap<>();

    // ── Category fill palette ─────────────────────────────────────────────────
    private static final int CAT_PLAINS     = 0xFF6A9E38;
    private static final int CAT_FOREST     = 0xFF2D7A2D;
    private static final int CAT_JUNGLE     = 0xFF1E8C30;
    private static final int CAT_DESERT     = 0xFFD4B238;
    private static final int CAT_BADLANDS   = 0xFFCC5C22;
    private static final int CAT_SAVANNA    = 0xFFB8A030;
    private static final int CAT_TAIGA      = 0xFF4A7C4E;
    private static final int CAT_WINDSWEPT  = 0xFF687870;
    private static final int CAT_MOUNTAIN   = 0xFF909090;
    private static final int CAT_SNOWY      = 0xFFD4E8F4;
    private static final int CAT_OCEAN      = 0xFF2471A3;
    private static final int CAT_DEEP_OCEAN = 0xFF0E2D45;
    private static final int CAT_SWAMP      = 0xFF5D6E2E;
    private static final int CAT_MUSHROOM   = 0xFF9B59B6;
    private static final int CAT_RIVER      = 0xFF5DADE2;
    private static final int CAT_CAVE       = 0xFF3A2820;

    static {
        // ── Plains ─────────────────────────────────────────────────────────────
        reg("minecraft:plains",           CAT_PLAINS,    0xFF4A7A20); // dark green
        reg("minecraft:sunflower_plains", CAT_PLAINS,    0xFFE8B820); // sunflower yellow
        reg("minecraft:meadow",           CAT_PLAINS,    0xFF60B8D0); // alpine sky blue

        // ── Forest ─────────────────────────────────────────────────────────────
        reg("minecraft:forest",                  CAT_FOREST, 0xFF1A5020); // dark green
        reg("minecraft:birch_forest",            CAT_FOREST, 0xFFD8D8C0); // birch-bark cream
        reg("minecraft:old_growth_birch_forest", CAT_FOREST, 0xFFB8A878); // aged tan
        reg("minecraft:flower_forest",           CAT_FOREST, 0xFFD060A0); // magenta-pink
        reg("minecraft:dark_forest",             CAT_FOREST, 0xFF101010); // near-black
        reg("minecraft:cherry_grove",            CAT_FOREST, 0xFFFF90B0); // bright cherry

        // ── Jungle ─────────────────────────────────────────────────────────────
        reg("minecraft:jungle",        CAT_JUNGLE, 0xFF0A5818); // deep canopy green
        reg("minecraft:sparse_jungle", CAT_JUNGLE, 0xFF70C850); // light leafy green
        reg("minecraft:bamboo_jungle", CAT_JUNGLE, 0xFFD0E040); // bamboo yellow-green

        // ── Desert ─────────────────────────────────────────────────────────────
        reg("minecraft:desert", CAT_DESERT, 0xFFB89020); // darker sand

        // ── Badlands ───────────────────────────────────────────────────────────
        reg("minecraft:badlands",        CAT_BADLANDS, 0xFF882E08); // deep rust
        reg("minecraft:eroded_badlands", CAT_BADLANDS, 0xFFFF9A40); // bright orange spires
        reg("minecraft:wooded_badlands", CAT_BADLANDS, 0xFF607828); // olive-green canopy

        // ── Savanna ────────────────────────────────────────────────────────────
        reg("minecraft:savanna",           CAT_SAVANNA, 0xFF886810); // dark brown-gold
        reg("minecraft:savanna_plateau",   CAT_SAVANNA, 0xFF503808); // deep earthy brown
        reg("minecraft:windswept_savanna", CAT_SAVANNA, 0xFFD8B860); // bright straw

        // ── Taiga ──────────────────────────────────────────────────────────────
        reg("minecraft:taiga",                   CAT_TAIGA, 0xFF284830); // dark conifer green
        reg("minecraft:old_growth_pine_taiga",   CAT_TAIGA, 0xFF8A5030); // pine-bark brown
        reg("minecraft:old_growth_spruce_taiga", CAT_TAIGA, 0xFF3090A0); // spruce blue-teal
        reg("minecraft:grove",                   CAT_TAIGA, 0xFFB0D8B8); // light sage
        reg("minecraft:snowy_taiga",             CAT_TAIGA, 0xFFB8D8E8); // icy blue

        // ── Windswept ──────────────────────────────────────────────────────────
        reg("minecraft:windswept_hills",          CAT_WINDSWEPT, 0xFF404848); // dark slate
        reg("minecraft:windswept_gravelly_hills", CAT_WINDSWEPT, 0xFFC0B8A0); // gravel tan
        reg("minecraft:windswept_forest",         CAT_WINDSWEPT, 0xFF204820); // dark forest-green

        // ── Mountain / Peaks ───────────────────────────────────────────────────
        reg("minecraft:stony_peaks",  CAT_MOUNTAIN, 0xFF505050); // dark gray
        reg("minecraft:stony_shore",  CAT_MOUNTAIN, 0xFFC0B898); // warm sandy
        reg("minecraft:jagged_peaks", CAT_MOUNTAIN, 0xFF4080A0); // cold blue-gray
        reg("minecraft:frozen_peaks", CAT_MOUNTAIN, 0xFFE0F0FF); // near-white ice

        // ── Snowy ──────────────────────────────────────────────────────────────
        reg("minecraft:snowy_plains",  CAT_SNOWY, 0xFF9AB8CC); // medium blue
        reg("minecraft:snowy_slopes",  CAT_SNOWY, 0xFF7098B0); // steeper dark blue
        reg("minecraft:snowy_beach",   CAT_SNOWY, 0xFF909888); // gray-green shore
        reg("minecraft:ice_spikes",    CAT_SNOWY, 0xFF60D0FF); // bright cyan spikes

        // ── Ocean ──────────────────────────────────────────────────────────────
        reg("minecraft:ocean",          CAT_OCEAN, 0xFF184E78); // dark standard blue
        reg("minecraft:warm_ocean",     CAT_OCEAN, 0xFF10A888); // tropical teal
        reg("minecraft:lukewarm_ocean", CAT_OCEAN, 0xFF2060A0); // cyan-blue
        reg("minecraft:cold_ocean",     CAT_OCEAN, 0xFF103858); // dark cold blue
        reg("minecraft:frozen_ocean",   CAT_OCEAN, 0xFFB0D8F0); // pale ice

        // ── Deep Ocean ─────────────────────────────────────────────────────────
        reg("minecraft:deep_ocean",          CAT_DEEP_OCEAN, 0xFF050D18); // near-black
        reg("minecraft:deep_lukewarm_ocean", CAT_DEEP_OCEAN, 0xFF085038); // deep teal
        reg("minecraft:deep_cold_ocean",     CAT_DEEP_OCEAN, 0xFF061828); // very dark blue
        reg("minecraft:deep_frozen_ocean",   CAT_DEEP_OCEAN, 0xFF6090B0); // slate-blue

        // ── Swamp ──────────────────────────────────────────────────────────────
        reg("minecraft:swamp",          CAT_SWAMP, 0xFF303818); // dark olive
        reg("minecraft:mangrove_swamp", CAT_SWAMP, 0xFF604820); // warm brown-olive

        // ── Mushroom ───────────────────────────────────────────────────────────
        reg("minecraft:mushroom_fields", CAT_MUSHROOM, 0xFF6A3080); // deep violet

        // ── River ──────────────────────────────────────────────────────────────
        reg("minecraft:river",        CAT_RIVER, 0xFF2888C0); // deeper blue
        reg("minecraft:frozen_river", CAT_RIVER, 0xFFB0D8F0); // icy pale

        // ── Cave / Underground ─────────────────────────────────────────────────
        reg("minecraft:lush_caves",      CAT_CAVE, 0xFF00B868); // vivid teal-green
        reg("minecraft:dripstone_caves", CAT_CAVE, 0xFF907060); // warm stone
        reg("minecraft:deep_dark",       CAT_CAVE, 0xFF101028); // dark indigo

        // ── Legacy / pre-1.18 names ────────────────────────────────────────────
        reg("minecraft:hills",                       CAT_MOUNTAIN, 0xFF486848);
        reg("minecraft:wooded_hills",                CAT_MOUNTAIN, 0xFF284828);
        reg("minecraft:mountains",                   CAT_MOUNTAIN, 0xFF606060);
        reg("minecraft:gravelly_mountains",          CAT_MOUNTAIN, 0xFFB0A890);
        reg("minecraft:modified_gravelly_mountains", CAT_MOUNTAIN, 0xFF909080);
        reg("minecraft:mountain_meadow",             CAT_MOUNTAIN, 0xFF509850);
        reg("minecraft:frozen_mountain_peaks",       CAT_MOUNTAIN, 0xFFD0E8F8);
        reg("minecraft:gravel_beach",                CAT_MOUNTAIN, 0xFFB0A890);
    }

    private static void reg(String id, int category, int border) {
        //? if >=1.21 {
        ResourceLocation loc = ResourceLocation.parse(id);
        //?} else
        /*ResourceLocation loc = new ResourceLocation(id);*/
        CATEGORY.put(loc, category);
        BORDER.put(loc, border);
    }

    /** Category fill color - written to the tile cache and drawn on the map. */
    public static int getCategoryColor(ResourceLocation biomeId) {
        Integer mapped = CATEGORY.get(biomeId);
        if (mapped != null) return mapped;
        if (!"minecraft".equals(biomeId.getNamespace())) {
            return inferCategoryFromId(biomeId.getPath());
        }
        return GenesisTheme.STEEL_GRAY;
    }

    /** Per-biome border color - used only in the legend swatch. */
    public static int getBorderColor(ResourceLocation biomeId) {
        Integer explicit = BORDER.get(biomeId);
        if (explicit != null) return explicit;
        if (!"minecraft".equals(biomeId.getNamespace())) {
            int cat = getCategoryColor(biomeId);
            if (cat != GenesisTheme.STEEL_GRAY) {
                // Darken the category color by 40% to give a visible border ring in the legend swatch.
                return 0xFF000000
                    | (((cat >> 16 & 0xFF) * 3 / 5) << 16)
                    | (((cat >>  8 & 0xFF) * 3 / 5) <<  8)
                    | ( (cat       & 0xFF) * 3 / 5);
            }
        }
        return 0xFF2A2A2A;
    }

    /**
     * Infers a category fill color from a biome's path segment using keyword matching.
     * This covers the vast majority of mod biomes without needing explicit registration.
     * Returns STEEL_GRAY for biomes that don't match any known pattern (will fall back
     * to deriveModdedBiomeColor in the preview thread).
     */
    private static int inferCategoryFromId(String path) {
        // Deep ocean before generic ocean
        if (path.contains("deep_ocean") || (path.contains("deep") && path.contains("ocean")))
            return CAT_DEEP_OCEAN;
        // Underground / cave biomes
        if (path.contains("cave") || path.contains("cavern") || path.contains("grotto")
                || path.contains("underground") || path.contains("subterranean"))
            return CAT_CAVE;
        // Rivers
        if (path.contains("river"))
            return CAT_RIVER;
        // Ocean / aquatic
        if (path.contains("ocean") || path.contains("reef") || path.contains("lagoon"))
            return CAT_OCEAN;
        // Mushroom / fungal islands
        if (path.contains("mushroom") || path.contains("mycelium"))
            return CAT_MUSHROOM;
        // Swamp / wetland
        if (path.contains("swamp") || path.contains("bog") || path.contains("bayou")
                || path.contains("mire") || path.contains("wetland") || path.contains("marsh"))
            return CAT_SWAMP;
        // Snowy / frozen / tundra
        if (path.contains("snowy") || path.contains("frozen") || path.contains("glacier")
                || path.contains("tundra") || path.contains("arctic") || path.contains("polar"))
            return CAT_SNOWY;
        // Badlands / mesa
        if (path.contains("badland") || path.contains("mesa"))
            return CAT_BADLANDS;
        // Volcanic / peaks / mountains (before windswept to avoid "windswept_hills" → mountain)
        if (path.contains("volcano") || path.contains("volcanic") || path.contains("caldera")
                || path.contains("peak") || path.contains("mountain") || path.contains("highland")
                || path.contains("cliff") || path.contains("summit") || path.contains("alpine")
                || path.contains("canyon"))
            return CAT_MOUNTAIN;
        // Windswept / sky islands
        if (path.contains("windswept") || path.contains("skylands"))
            return CAT_WINDSWEPT;
        // Savanna / baobab / outback
        if (path.contains("savanna") || path.contains("baobab") || path.contains("outback"))
            return CAT_SAVANNA;
        // Desert / dunes / arid
        if (path.contains("desert") || path.contains("dune") || path.contains("arid")
                || path.contains("wasteland"))
            return CAT_DESERT;
        // Jungle / rainforest / tropical
        if (path.contains("jungle") || path.contains("amazon")
                || path.contains("rainforest") || path.contains("tropical"))
            return CAT_JUNGLE;
        // Taiga / boreal / conifer
        if (path.contains("taiga") || path.contains("conifer") || path.contains("boreal")
                || path.contains("pine") || path.contains("spruce") || path.contains("fir"))
            return CAT_TAIGA;
        // Forest / woodland / grove / glade
        if (path.contains("forest") || path.contains("wood") || path.contains("grove")
                || path.contains("glade"))
            return CAT_FOREST;
        // Plains / meadows / valleys / shrublands
        if (path.contains("plain") || path.contains("meadow") || path.contains("field")
                || path.contains("valley") || path.contains("grassland") || path.contains("prairie")
                || path.contains("steppe") || path.contains("shrub") || path.contains("lavender"))
            return CAT_PLAINS;
        return GenesisTheme.STEEL_GRAY;
    }

    /**
     * Reconstructs a ResourceLocation from the translation key stored in the tile cache.
     * Format: "biome.{namespace}.{path}" → ResourceLocation("{namespace}", "{path}")
     */
    public static ResourceLocation locationFromKey(String translationKey) {
        String rest = translationKey.substring(6); // strip "biome."
        int dot = rest.indexOf('.');
        //? if >=1.21 {
        if (dot < 0) return ResourceLocation.parse(rest);
        return ResourceLocation.fromNamespaceAndPath(rest.substring(0, dot), rest.substring(dot + 1));
        //?} else {
        /*if (dot < 0) return new ResourceLocation(rest);
        return new ResourceLocation(rest.substring(0, dot), rest.substring(dot + 1));
        *///?}
    }

    private BiomeColorMap() {}
}
