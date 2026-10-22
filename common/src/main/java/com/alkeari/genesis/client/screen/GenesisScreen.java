package com.alkeari.genesis.client.screen;

import com.mojang.datafixers.util.Pair;
import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.genesis.mixin.CreateWorldScreenInvoker;
import com.alkeari.genesis.mixin.MultiNoiseBiomeSourceAccessor;
import com.alkeari.genesis.pregen.PendingPreGen;
import com.alkeari.genesis.pregen.PendingSpawn;
import com.alkeari.genesis.client.preview.BiolithReplacements;
import com.alkeari.genesis.client.preview.BiomeColorMap;
import com.alkeari.genesis.client.preview.BiomePreviewThread;
import com.alkeari.genesis.client.preview.BiomeTileCache;
import com.alkeari.genesis.map.StructureLocator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Tooltip;

public final class GenesisScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisScreen");

    private static final int TOP_BAR_HEIGHT = 28;
    private static final int RIGHT_PANEL_W  = 160;
    private static final int RP_PADDING     = 8;

    // Right-panel layout (relative to screen top), every offset on the design system's 4 px grid
    private static final int RP_SEED_LABEL_Y   = TOP_BAR_HEIGHT + 8;
    private static final int RP_SEED_FIELD_Y   = TOP_BAR_HEIGHT + 20;
    private static final int RP_SEED_FIELD_H   = 20;
    private static final int RP_SEED_BTN_Y     = TOP_BAR_HEIGHT + 44;
    private static final int RP_DIVIDER_1_Y    = TOP_BAR_HEIGHT + 64;
    private static final int RP_ZOOM_LABEL_Y   = TOP_BAR_HEIGHT + 72;
    private static final int RP_ZOOM_BTN_Y     = TOP_BAR_HEIGHT + 84;
    private static final int RP_DIVIDER_2_Y    = TOP_BAR_HEIGHT + 104;
    private static final int RP_SPAWN_LABEL_Y  = TOP_BAR_HEIGHT + 112;
    private static final int RP_SPAWN_ROW_Y    = TOP_BAR_HEIGHT + 124;
    private static final int RP_DIVIDER_3_Y    = TOP_BAR_HEIGHT + 144;
    private static final int RP_SPAWN_RESET_W  = 48;
    // The legend moves up into the spawn section's place when spawn picking is turned off.
    private static final int RP_SPAWN_SECTION_H = 40;
    private static final int RP_LEGEND_LABEL_Y = TOP_BAR_HEIGHT + 152;
    private static final int RP_LEGEND_Y       = TOP_BAR_HEIGHT + 164;

    private final CreateWorldScreen parent;
    private final WorldCreationContext context;

    private final BiomeTileCache tileCache = new BiomeTileCache();
    private final MapViewport viewport     = new MapViewport();
    private final LegendPanel legend       = new LegendPanel();

    private BiomePreviewThread[] previewThreads = new BiomePreviewThread[0];
    private BiolithReplacements biolithReplacements;
    private boolean previewUnavailable = false;
    // Set when the preview failed rather than the world type having no generator to preview.
    private boolean previewFailed = false;
    private LevelStem overworldStem;

    private long currentSeed;
    private NoiseBasedChunkGenerator noiseGen;
    private NoiseGeneratorSettings noiseSettings;
    private BiomeSource fallbackBiomeSource; // non-NoiseBasedChunkGenerator generators

    private EditBox seedField;
    private long toastUntil = 0;
    private String toastText = "";
    private int toastY;
    private int toastColor;
    private int exportBtnY;

    private boolean spawnChosen = false;
    private int spawnBlockX;
    private int spawnBlockZ;
    private GenesisButton spawnResetBtn;

    private StructureLocator structureLocator;
    private boolean structuresShown = true;
    private int structuresBtnY;

    private PreGenConfigOverlay pregenOverlay = null;

    public GenesisScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.translatable("genesis.screen.title"));
        this.parent  = parent;
        this.context = context;
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        pregenOverlay = null;
        buildPreviewState();

        int mapW = this.width - RIGHT_PANEL_W;
        int mapH = this.height - TOP_BAR_HEIGHT;
        viewport.setBounds(0, TOP_BAR_HEIGHT, mapW, mapH);

        int rpX = this.width - RIGHT_PANEL_W + RP_PADDING;
        int rpW = RIGHT_PANEL_W - RP_PADDING * 2;   // usable inner width

        // Back - top-left
        addRenderableWidget(GenesisButton.of(4, 4, 56, 20, "genesis.screen.back", this::onClose)
                .tip("genesis.screen.back.tip"));

        // Seed EditBox (borderless - we draw the box in drawRightPanel), inset 4 px inside that box
        seedField = new EditBox(this.font, rpX + 4, RP_SEED_FIELD_Y + 2, rpW - 8, 16, Component.empty());
        seedField.setTooltip(Tooltip.create(Component.translatable("genesis.screen.seed.tip")));
        seedField.setMaxLength(32);
        seedField.setValue(String.valueOf(currentSeed));
        seedField.setTextColor(GenesisTheme.SOFT_CHAMPAGNE);
        seedField.setHint(Component.translatable("genesis.screen.seed.hint"));
        seedField.setBordered(false);
        addRenderableWidget(seedField);

        // Seed buttons - equal-width split
        int halfW = (rpW - 4) / 2;
        addRenderableWidget(GenesisButton.of(rpX,          RP_SEED_BTN_Y, halfW, 16, "genesis.screen.random", this::randomizeSeed)
                .tip("genesis.screen.random.tip"));
        addRenderableWidget(GenesisButton.of(rpX + halfW + 4, RP_SEED_BTN_Y, halfW, 16, "genesis.screen.copy", this::copySeed)
                .tip("genesis.screen.copy.tip"));

        // Zoom toggle buttons - one equal part per level, 4 px apart
        ZoomLevel[] levels = ZoomLevel.values();
        int zoomW = (rpW - 4 * (levels.length - 1)) / levels.length;
        for (int i = 0; i < levels.length; i++) {
            ZoomLevel level = levels[i];
            String key = "genesis.zoom." + level.name().toLowerCase(java.util.Locale.ROOT);
            addRenderableWidget(GenesisButton.toggle(
                    rpX + (zoomW + 4) * i, RP_ZOOM_BTN_Y, zoomW, 16, key,
                    () -> viewport.setZoom(level),
                    () -> viewport.getZoom() == level)
                    .tip(key + ".tip"));
        }

        // Spawn reset - shown only while a spawn point is chosen
        spawnResetBtn = GenesisButton.of(rpX + rpW - RP_SPAWN_RESET_W, RP_SPAWN_ROW_Y, RP_SPAWN_RESET_W, 16,
                "genesis.screen.spawn.reset", this::resetSpawn).tip("genesis.screen.spawn.reset.tip");
        spawnResetBtn.visible = spawnChosen;
        addRenderableWidget(spawnResetBtn);

        // Bottom action buttons - CREATE WORLD above PRE-GENERATE (full width, stacked)
        int preBtnY    = this.height - RP_PADDING - 24;
        int createBtnY = preBtnY - 4 - 24;
        exportBtnY     = createBtnY - 8 - 16;
        structuresBtnY = exportBtnY - 4 - 16;

        if (GenesisConfig.showStructures) {
            addRenderableWidget(GenesisButton.toggle(rpX, structuresBtnY, rpW, 16, "genesis.structure.toggle",
                    () -> structuresShown = !structuresShown, () -> structuresShown)
                    .tip("genesis.structure.toggle.tip"));
        }

        addRenderableWidget(GenesisButton.of(rpX, exportBtnY, rpW, 16, "genesis.screen.export", this::exportMap)
                .tip("genesis.screen.export.tip"));

        addRenderableWidget(GenesisButton.primary(
                rpX, createBtnY, rpW, 24, "genesis.screen.create",
                () -> {
                    currentSeed = parseSeedFromField();
                    applySeedToParent();
                    passSpawnToNewWorld();
                    stopPreviewThread();
                    tryGeoGradientReset();
                    ((CreateWorldScreenInvoker) parent).genesis_createWorld();
                }
        ).tip("genesis.screen.create.tip"));

        addRenderableWidget(GenesisButton.of(
                rpX, preBtnY, rpW, 24, "genesis.screen.pregen",
                () -> pregenOverlay = new PreGenConfigOverlay(
                        this.font, this.width, this.height,
                        () -> pregenOverlay = null,
                        (r, c) -> {
                            PendingPreGen.set(r, c);
                            passSpawnToNewWorld();
                            stopPreviewThread();
                            tryGeoGradientReset();
                            ((CreateWorldScreenInvoker) parent).genesis_createWorld();
                        })
        ).tip("genesis.screen.pregen.tip"));

        startPreviewThread();
    }

    @Override
    public void onClose() {
        currentSeed = parseSeedFromField();
        applySeedToParent();
        stopPreviewThread();
        tryGeoGradientReset();
        //noinspection DataFlowIssue
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        stopPreviewThread();
        if (com.alkeari.genesis.map.AccuracyCheck.ENABLED) writePreviewForAccuracyCheck();
    }

    /** Writes every sampled tile as "chunkX chunkZ biome" for the server's accuracy check to compare with the world. */
    private void writePreviewForAccuracyCheck() {
        java.nio.file.Path file = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
                .resolve("genesis-preview-" + currentSeed + ".txt");
        int radius = 64;
        StringBuilder out = new StringBuilder();
        for (int cz = -radius; cz <= radius; cz++) {
            for (int cx = -radius; cx <= radius; cx++) {
                String id = tileCache.getBiomeId(cx, cz);
                if (id != null) out.append(cx).append(' ').append(cz).append(' ').append(id).append('\n');
            }
        }
        try {
            java.nio.file.Files.writeString(file, out);
            LOGGER.info("[Genesis verify] Wrote the previewed biomes to {}", file);
        } catch (java.io.IOException e) {
            LOGGER.warn("[Genesis verify] Could not write {}", file, e);
        }
    }

    @Override
    public void tick() {
        //? if <1.20.2
        /*if (seedField != null) seedField.tick();*/
        if (pregenOverlay != null) pregenOverlay.tick();
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pregenOverlay != null) return pregenOverlay.keyPressed(keyCode, scanCode, modifiers);
        if (seedField != null) {
            if (seedField.isFocused()) {
                if (keyCode == 257 || keyCode == 335) { // Enter / numpad-Enter
                    applySeedFromField();
                    return true;
                }
            } else {
                if (keyCode == 82) { // R
                    randomizeSeed();
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pregenOverlay != null) return pregenOverlay.charTyped(chr, modifiers);
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pregenOverlay != null) return pregenOverlay.mouseClicked(mouseX, mouseY, button);
        if (button == 1 && spawnPickingOn() && viewport.isInBounds(mouseX, mouseY)) {
            // The middle of the chunk under the cursor, so the marker sits where the map tile is.
            spawnBlockX = viewport.screenToChunkX(mouseX) * 16 + 8;
            spawnBlockZ = viewport.screenToChunkZ(mouseY) * 16 + 8;
            spawnChosen = true;
            spawnResetBtn.visible = true;
            return true;
        }
        viewport.onMousePressed(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        viewport.onMouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        viewport.onMouseDragged(mouseX, mouseY);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (legend.scroll(mouseX, mouseY, scrollY)) return true;
        if (viewport.isInBounds(mouseX, mouseY)) {
            viewport.onScroll(mouseX, mouseY, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (legend.scroll(mouseX, mouseY, delta)) return true;
        if (viewport.isInBounds(mouseX, mouseY)) {
            viewport.onScroll(mouseX, mouseY, delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    @Override
    public boolean isPauseScreen() { return false; }

    // -------------------------------------------------------------------------
    // Preview state
    // -------------------------------------------------------------------------

    private void buildPreviewState() {
        try {
            currentSeed = context.options().seed();
            // A data pack's overworld (William Wythers' Expanded Ecosphere ships one) replaces the world preset's
            // when the world is created, so the preview takes it first.
            java.util.Optional<LevelStem> overworldOpt = context.datapackDimensions().getOptional(LevelStem.OVERWORLD)
                    .or(() -> context.selectedDimensions().get(LevelStem.OVERWORLD));
            if (overworldOpt.isEmpty()) { previewUnavailable = true; return; }
            overworldStem = overworldOpt.get();
            ChunkGenerator gen = overworldStem.generator();
            if (gen instanceof NoiseBasedChunkGenerator ng) {
                noiseGen      = ng;
                noiseSettings = ng.generatorSettings().value();
            } else {
                // Custom generator (e.g. FlatLevelSource for Superflat, or a mod's generator).
                // We can still show biomes using its BiomeSource with a zero-climate sampler.
                fallbackBiomeSource = gen.getBiomeSource();
            }
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to extract world generator", t);
            previewUnavailable = true;
            previewFailed = true;
        }
    }

    private void startPreviewThread() {
        stopPreviewThread();
        tileCache.clear();
        if (previewUnavailable || !GenesisConfig.previewEnabled) return;
        try {
            BiomeSource source;
            Climate.Sampler sampler;
            RandomState randomState;

            if (noiseGen != null) {
                randomState = RandomState.create(
                        noiseSettings,
                        context.worldgenRegistries().compositeAccess().lookupOrThrow(Registries.NOISE),
                        currentSeed
                );
                sampler = randomState.sampler();
                source  = lithostitchedPreviewSource(previewSource(noiseGen.getBiomeSource(), currentSeed), currentSeed);
                tryGeoGradientInit(currentSeed, source);
            } else if (fallbackBiomeSource != null) {
                // Custom / flat generator - build a sampler from default overworld noise
                // settings. FixedBiomeSource (Superflat) ignores the sampler entirely;
                // other custom sources will sample as if they were vanilla overworld climate.
                var noiseLookup    = context.worldgenRegistries().compositeAccess()
                        .lookupOrThrow(Registries.NOISE);
                var settingsLookup = context.worldgenRegistries().compositeAccess()
                        .lookupOrThrow(Registries.NOISE_SETTINGS);
                var settingsOpt    = settingsLookup.get(NoiseGeneratorSettings.OVERWORLD);
                if (settingsOpt.isEmpty()) { previewUnavailable = true; return; }
                randomState = RandomState.create(settingsOpt.get().value(), noiseLookup, currentSeed);
                sampler = randomState.sampler();
                source  = fallbackBiomeSource;
                if (!(source instanceof FixedBiomeSource)) tileCache.markApproximate("genesis.approximate.climate");
            } else {
                return;
            }

            // Get the biome registry for Holder.Direct reverse-lookup (Corgilib / wrapped biomes).
            Registry<Biome> biomeRegistry = null;
            try {
                biomeRegistry = context.worldgenRegistries().compositeAccess()
                        .registryOrThrow(Registries.BIOME);
            } catch (Exception e) {
                LOGGER.debug("[Genesis] Could not obtain biome registry for reverse lookup", e);
            }

            // Build a TerraBlender fallback parameter list so that if the live biomeSource
            // throws (ClassCastException on TBSampler, Biolith NoSuchElementException, etc.)
            // the preview threads can still produce correct biome colours via vanilla
            // ParameterPoint fitness lookups without needing TerraBlender's extended sampler.
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> tbParams =
                    tryBuildTerraBlenderParams(context.worldgenRegistries().compositeAccess(), source);

            int numThreads = GenesisConfig.previewThreads > 0
                    ? GenesisConfig.previewThreads
                    : Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            previewThreads = new BiomePreviewThread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                previewThreads[i] = new BiomePreviewThread(
                        source, sampler, tileCache,
                        i, numThreads,
                        biomeRegistry,
                        tbParams,
                        () -> viewport.screenToChunkX(0) - 2,
                        () -> viewport.screenToChunkX(this.width - RIGHT_PANEL_W) + 2,
                        () -> viewport.screenToChunkZ(TOP_BAR_HEIGHT) - 2,
                        () -> viewport.screenToChunkZ(this.height) + 2
                ).withBiolith(biolithReplacements);
                previewThreads[i].start();
            }

            if (GenesisConfig.showStructures && noiseGen != null) startStructureLocator(randomState, source, sampler);
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to start biome preview", t);
            previewUnavailable = true;
            previewFailed = true;
        }
    }

    // Kept apart from the biome preview so a structure mod the locator cannot read costs only the markers.
    private void startStructureLocator(RandomState randomState, BiomeSource source, Climate.Sampler sampler) {
        try {
            var structureState = ChunkGeneratorStructureState.createForNormal(randomState, currentSeed, source,
                    context.worldgenRegistries().compositeAccess().lookupOrThrow(Registries.STRUCTURE_SET));
            structureLocator = new StructureLocator(structureState, source, sampler,
                    () -> viewport.visibleMinChunkX(), () -> viewport.visibleMaxChunkX(),
                    () -> viewport.visibleMinChunkZ(), () -> viewport.visibleMaxChunkZ());
            structureLocator.start();
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to start the structure locator; the map shows no structure markers", t);
            structureLocator = null;
        }
    }

    private static Climate.ParameterList<Holder<Biome>> parametersOf(MultiNoiseBiomeSource source) {
        return ((MultiNoiseBiomeSourceAccessor) source).genesis_parameters()
                .map(list -> list, holder -> holder.value().parameters());
    }

    // The preview samples its own source, never the world's: Biolith caches a source's biome entries on its
    // first lookup, and before the server starts that cache would hold the list without Biolith's biomes.
    private BiomeSource previewSource(BiomeSource source, long seed) {
        if (!(source instanceof MultiNoiseBiomeSource multiNoise)) return source;
        // A private list: Lithostitched rewrites the list it is given in place, so the world's own must never reach it.
        Climate.ParameterList<Holder<Biome>> list = new Climate.ParameterList<>(
                new ArrayList<>(biolithPreviewList(parametersOf(multiNoise), seed).values()));
        BiomeSource terraBlender = terraBlenderPreviewSource(list, seed);
        return terraBlender != null ? terraBlender : MultiNoiseBiomeSource.createFromList(list);
    }

    // Biolith (Traverse among the mods using it) adds its biome placements to a source's parameter list only
    // once a server has started and named the source's dimension, so the preview applies the same removals
    // and placements to its own copy, and rebuilds the replacements the server will choose. Sub-biomes depend
    // on the running world, so where one applies the preview shows the biome it replaces.
    private Climate.ParameterList<Holder<Biome>> biolithPreviewList(Climate.ParameterList<Holder<Biome>> list, long seed) {
        biolithReplacements = null;
        Field registryField = null;
        Object previousRegistries = null;
        Field injectedField = null;
        Object overworldPlacement = null;
        boolean previousInjected = false;
        try {
            Class<?> coordinator = Class.forName("com.terraformersmc.biolith.impl.biome.BiomeCoordinator");
            Object overworld = coordinator.getField("OVERWORLD").get(null);
            // On Fabric, Biolith learns the biome registry only when a server starts, and its placements throw
            // without it. The preview lends it the creation screen's registries and puts back what it held.
            Method lookup = methodNamed(coordinator, "getBiomeLookup");
            Method setRegistries = methodNamed(coordinator, "setRegistryManager");
            if (lookup != null && setRegistries != null && ((Optional<?>) lookup.invoke(null)).isEmpty()) {
                registryField = coordinator.getDeclaredField("registryManager");
                registryField.setAccessible(true);
                previousRegistries = registryField.get(null);
                setRegistries.invoke(null, context.worldgenRegistries());
            }
            // writeBiomeEntries marks Biolith's placements as written, after which it drops every request added
            // before the world's server starts (those its data loader reads among them), so the mark is put back.
            injectedField = Class.forName("com.terraformersmc.biolith.impl.biome.DimensionBiomePlacement").getDeclaredField("biomesInjected");
            injectedField.setAccessible(true);
            overworldPlacement = overworld;
            previousInjected = injectedField.getBoolean(overworld);
            Method removalFilter = overworld.getClass().getMethod("removalFilter", Pair.class);
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> entries = new ArrayList<>();
            for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : list.values()) {
                if ((boolean) removalFilter.invoke(overworld, entry)) entries.add(entry);
            }
            int kept = entries.size();
            Consumer<Pair<Climate.ParameterPoint, Holder<Biome>>> add = entries::add;
            overworld.getClass().getMethod("writeBiomeEntries", Consumer.class).invoke(overworld, add);
            LOGGER.info("[Genesis] Applied Biolith to the preview: {} entries removed, {} added",
                    list.values().size() - kept, entries.size() - kept);
            try {
                biolithReplacements = BiolithReplacements.of(overworld, seed,
                        context.worldgenRegistries().compositeAccess().registryOrThrow(Registries.BIOME));
                if (biolithReplacements != null) LOGGER.info("[Genesis] Applied Biolith's biome replacements to the preview");
            } catch (Throwable t) {
                LOGGER.warn("[Genesis] Could not rebuild Biolith's biome replacements for the preview", t);
                tileCache.markApproximate("genesis.approximate.biolith");
            }
            Field subBiomes = Class.forName("com.terraformersmc.biolith.impl.biome.DimensionBiomePlacement").getDeclaredField("subBiomeRequests");
            subBiomes.setAccessible(true);
            if (!((Map<?, ?>) subBiomes.get(overworld)).isEmpty()) tileCache.markApproximate("genesis.approximate.biolith_sub");
            return new Climate.ParameterList<>(entries);
        } catch (ClassNotFoundException e) {
            return list;
        } catch (Throwable t) {
            LOGGER.warn("[Genesis] Could not apply Biolith biome placements to the preview", t);
            tileCache.markApproximate("genesis.approximate.missing");
            return list;
        } finally {
            if (injectedField != null) {
                try {
                    injectedField.setBoolean(overworldPlacement, previousInjected);
                } catch (IllegalAccessException e) {
                    LOGGER.warn("[Genesis] Could not restore Biolith's placement state after the preview", e);
                }
            }
            if (registryField != null) {
                try {
                    registryField.set(null, previousRegistries);
                } catch (IllegalAccessException e) {
                    LOGGER.warn("[Genesis] Could not restore Biolith's registry after the preview", e);
                }
            }
        }
    }

    private static Method methodNamed(Class<?> owner, String name) {
        for (Method m : owner.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == (name.startsWith("set") ? 1 : 0)) return m;
        }
        return null;
    }

    // Lithostitched places its biome injectors (Regions Unexplored among the mods using them) into each
    // dimension's generator only when a server starts. The preview runs the same placement on a one-dimension
    // registry holding a copy of the generator, so the world's own generator is never touched.
    private BiomeSource lithostitchedPreviewSource(BiomeSource source, long seed) {
        if (overworldStem == null || noiseGen == null) return source;
        try {
            Class<?> manager = Class.forName("dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.BiomeInjectorManager");
            NoiseBasedChunkGenerator copy = new NoiseBasedChunkGenerator(source, noiseGen.generatorSettings());
            MappedRegistry<LevelStem> stems = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
            Registry.register(stems, LevelStem.OVERWORLD, new LevelStem(overworldStem.type(), copy));
            manager.getMethod("applyBiomeInjectors", RegistryAccess.class, Registry.class, long.class)
                   .invoke(null, context.worldgenRegistries().compositeAccess(), stems, seed);
            BiomeSource injected = copy.getBiomeSource();
            if (injected != source) LOGGER.info("[Genesis] Applied Lithostitched biome injectors to the preview");
            return injected;
        } catch (ClassNotFoundException e) {
            return source;
        } catch (Throwable t) {
            LOGGER.warn("[Genesis] Could not apply Lithostitched biome injectors to the preview", t);
            tileCache.markApproximate("genesis.approximate.missing");
            return source;
        }
    }

    // TerraBlender fills its regions into a parameter list only when a server starts, so before that the
    // live source answers every sample with its deferred placeholder biome. The preview initializes its own
    // fresh list for the previewed seed, so a list a world already initialized for another seed is never used.
    @SuppressWarnings("unchecked")
    private BiomeSource terraBlenderPreviewSource(Climate.ParameterList<Holder<Biome>> list, long seed) {
        try {
            Class<?> extended = Class.forName("terrablender.worldgen.IExtendedParameterList");
            if (!extended.isInstance(list)) return null;
            Object overworld = Class.forName("terrablender.api.RegionType").getField("OVERWORLD").get(null);
            for (Method m : extended.getMethods()) {
                if (m.getName().equals("initializeForTerraBlender")) {
                    m.invoke(list, context.worldgenRegistries().compositeAccess(), overworld, seed);
                }
            }
            LOGGER.info("[Genesis] Initialized TerraBlender regions for the preview");
            return MultiNoiseBiomeSource.createFromList(list);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[Genesis] Could not initialize TerraBlender regions for the preview", t);
            tileCache.markApproximate("genesis.approximate.missing");
            return null;
        }
    }

    private void stopPreviewThread() {
        for (BiomePreviewThread t : previewThreads) t.interrupt();
        for (BiomePreviewThread t : previewThreads) {
            try { t.join(200); } catch (InterruptedException ignored) {}
        }
        previewThreads = new BiomePreviewThread[0];
        if (structureLocator != null) structureLocator.interrupt();
        structureLocator = null;
    }

    // -------------------------------------------------------------------------
    // Seed actions
    // -------------------------------------------------------------------------

    private void randomizeSeed() {
        currentSeed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        if (seedField != null) seedField.setValue(String.valueOf(currentSeed));
        startPreviewThread();
    }

    private void copySeed() {
        //noinspection DataFlowIssue
        this.minecraft.keyboardHandler.setClipboard(String.valueOf(currentSeed));
        showToast(I18n.get("genesis.screen.copied"), RP_SEED_BTN_Y + 4, GenesisTheme.FORGED_GOLD);
    }

    private void exportMap() {
        try {
            //noinspection DataFlowIssue
            java.nio.file.Path file = com.alkeari.genesis.client.preview.MapExporter.export(tileCache,
                    viewport.visibleMinChunkX(), viewport.visibleMaxChunkX(),
                    viewport.visibleMinChunkZ(), viewport.visibleMaxChunkZ(),
                    currentSeed, spawnChosen && spawnPickingOn(), spawnBlockX, spawnBlockZ,
                    this.minecraft.gameDirectory.toPath().resolve("screenshots"));
            LOGGER.info("[Genesis] Exported the preview map to {}", file);
            showToast(I18n.get("genesis.screen.exported"), exportBtnY + 4, GenesisTheme.FORGED_GOLD);
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Could not export the preview map", t);
            showToast(I18n.get("genesis.screen.export_failed"), exportBtnY + 4, GenesisTheme.ALERT_AMBER);
        }
    }

    private void showToast(String text, int y, int color) {
        toastText  = text;
        toastY     = y;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 2000;
    }

    private long parseSeedFromField() {
        if (seedField == null) return currentSeed;
        String text = seedField.getValue().trim();
        if (text.isEmpty()) return currentSeed;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return text.hashCode();
        }
    }

    private void applySeedFromField() {
        long parsed = parseSeedFromField();
        if (parsed == currentSeed) return;
        currentSeed = parsed;
        if (seedField != null) seedField.setValue(String.valueOf(currentSeed));
        startPreviewThread();
    }

    private void applySeedToParent() {
        try {
            parent.getUiState().setSeed(String.valueOf(currentSeed));
        } catch (Exception e) {
            LOGGER.error("[Genesis] Failed to apply seed to parent screen", e);
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // GenesisScreen draws its own full-screen background; suppress the default overlay.
    }
    //?}

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Base fill
        gfx.fill(0, 0, this.width, this.height, GenesisTheme.OBSIDIAN_BLACK);

        drawTopBar(gfx);

        if (previewUnavailable) {
            drawUnavailableMessage(gfx);
        } else {
            viewport.render(gfx, tileCache);
            if (structuresShown && structureLocator != null) {
                StructureMarkers.draw(gfx, viewport, structureLocator.markers(),
                        0, TOP_BAR_HEIGHT, this.width - RIGHT_PANEL_W, this.height);
            }
            drawChosenSpawn(gfx);
            drawScaleBar(gfx);
            drawCoordinateReadout(gfx, mouseX, mouseY);
            drawZoomLabel(gfx);
        }

        // Gold divider: map | right panel
        int divX = this.width - RIGHT_PANEL_W;
        gfx.fill(divX, TOP_BAR_HEIGHT, divX + 1, this.height, GenesisTheme.FORGED_GOLD);

        drawRightPanel(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);

        if (pregenOverlay != null) {
            pregenOverlay.render(gfx, this.font, mouseX, mouseY);
        } else if (!previewUnavailable && structuresShown && structureLocator != null) {
            StructureMarkers.drawHover(gfx, this.font, viewport, structureLocator.markers(), mouseX, mouseY);
        }

        drawToast(gfx);
    }

    private void drawTopBar(GuiGraphics gfx) {
        // Depth gradient: slightly lighter at top → deep graphite at bottom
        gfx.fill(0, 0, this.width, TOP_BAR_HEIGHT, GenesisTheme.DEEP_GRAPHITE);
        gfx.fill(0, TOP_BAR_HEIGHT / 2, this.width, TOP_BAR_HEIGHT, GenesisTheme.DEEP_GRAPHITE);
        // Gold bottom rule
        gfx.fill(0, TOP_BAR_HEIGHT - 1, this.width, TOP_BAR_HEIGHT, GenesisTheme.FORGED_GOLD);

        String title = I18n.get("genesis.screen.banner");
        int titleW = this.font.width(title);
        int titleX = (this.width - titleW) / 2;
        int titleY = (TOP_BAR_HEIGHT - 8) / 2;
        // Shadow pass
        gfx.drawString(this.font, title, titleX + 1, titleY + 1, GenesisTheme.WARM_GOLD_SHADOW, false);
        gfx.drawString(this.font, title, titleX,     titleY,     GenesisTheme.FORGED_GOLD,      false);
    }

    private void drawRightPanel(GuiGraphics gfx) {
        int rpX   = this.width - RIGHT_PANEL_W;
        int inner = rpX + RP_PADDING;
        int rpW   = RIGHT_PANEL_W - RP_PADDING * 2;

        // Panel background
        gfx.fill(rpX, TOP_BAR_HEIGHT, this.width, this.height, GenesisTheme.DEEP_GRAPHITE);

        // SEED section label
        drawSectionLabel(gfx, inner, RP_SEED_LABEL_Y, I18n.get("genesis.screen.seed"));

        // Custom EditBox border: focused = gold 1px, unfocused = dark border
        boolean focused = seedField != null && seedField.isFocused();
        int fieldBorder = focused ? GenesisTheme.FORGED_GOLD : GenesisTheme.HAIRLINE;
        gfx.fill(inner, RP_SEED_FIELD_Y, inner + rpW, RP_SEED_FIELD_Y + RP_SEED_FIELD_H, GenesisTheme.RAISED_FILL);
        GenesisTheme.drawBorder(gfx, inner, RP_SEED_FIELD_Y, rpW, RP_SEED_FIELD_H, fieldBorder);
        if (focused && this.minecraft.getLastInputType().isKeyboard()) {
            GenesisTheme.drawFocusRing(gfx, inner, RP_SEED_FIELD_Y, rpW, RP_SEED_FIELD_H);
        }

        // Divider 1
        drawDivider(gfx, inner, RP_DIVIDER_1_Y, rpW);

        // ZOOM section label
        drawSectionLabel(gfx, inner, RP_ZOOM_LABEL_Y, I18n.get("genesis.screen.zoom"));

        // Divider 2
        drawDivider(gfx, inner, RP_DIVIDER_2_Y, rpW);

        int legendShift = 0;
        if (spawnPickingOn()) {
            // SPAWN section: the chosen point, or how to choose one
            drawSectionLabel(gfx, inner, RP_SPAWN_LABEL_Y, I18n.get("genesis.screen.spawn"));
            String value = spawnChosen
                    ? I18n.get("genesis.screen.coords", spawnBlockX, spawnBlockZ)
                    : I18n.get("genesis.screen.spawn.hint");
            int valueW = spawnChosen ? rpW - RP_SPAWN_RESET_W - 4 : rpW;
            gfx.drawString(this.font, fitWidth(value, valueW), inner, RP_SPAWN_ROW_Y + 4,
                    spawnChosen ? GenesisTheme.SOFT_CHAMPAGNE : GenesisTheme.STEEL_GRAY, false);
            drawDivider(gfx, inner, RP_DIVIDER_3_Y, rpW);
        } else {
            legendShift = RP_SPAWN_SECTION_H;
        }

        // BIOME LEGEND section label
        drawSectionLabel(gfx, inner, RP_LEGEND_LABEL_Y - legendShift, I18n.get("genesis.screen.legend"));
        drawBiomeLegend(gfx, inner, RP_LEGEND_Y - legendShift);
    }

    private boolean spawnPickingOn() {
        return GenesisConfig.spawnFromMap && !previewUnavailable;
    }

    private void resetSpawn() {
        spawnChosen = false;
        spawnResetBtn.visible = false;
    }

    private void passSpawnToNewWorld() {
        if (spawnChosen && spawnPickingOn()) PendingSpawn.set(spawnBlockX, spawnBlockZ);
        else PendingSpawn.clear();
    }

    /** A gold ring around a gold dot, apart from the origin crosshair, where the chosen spawn point lies. */
    private void drawChosenSpawn(GuiGraphics gfx) {
        if (!spawnChosen || !spawnPickingOn()) return;
        int ppc = viewport.getZoom().pixelsPerChunk;
        int sx  = viewport.chunkToScreenX(Math.floorDiv(spawnBlockX, 16)) + ppc / 2;
        int sy  = viewport.chunkToScreenZ(Math.floorDiv(spawnBlockZ, 16)) + ppc / 2;
        int mapW = this.width - RIGHT_PANEL_W;
        gfx.enableScissor(0, TOP_BAR_HEIGHT, mapW, this.height);
        gfx.fill(sx - 5, sy - 5, sx + 6, sy + 6, GenesisTheme.OBSIDIAN_BLACK);
        GenesisTheme.drawBorder(gfx, sx - 4, sy - 4, 9, 9, GenesisTheme.FORGED_GOLD);
        gfx.fill(sx - 1, sy - 1, sx + 2, sy + 2, GenesisTheme.FORGED_GOLD);
        gfx.disableScissor();
    }

    /** Gold 2px left accent bar + section heading in steel gray */
    private void drawSectionLabel(GuiGraphics gfx, int x, int y, String label) {
        gfx.fill(x, y, x + 2, y + 8, GenesisTheme.FORGED_GOLD);
        gfx.drawString(this.font, label, x + 8, y, GenesisTheme.STEEL_GRAY, false);
    }

    private void drawDivider(GuiGraphics gfx, int x, int y, int w) {
        gfx.fill(x, y, x + w, y + 1, GenesisTheme.HAIRLINE);
    }

    private void drawBiomeLegend(GuiGraphics gfx, int x, int y) {
        legend.draw(gfx, this.font, tileCache, viewport, x, y, RIGHT_PANEL_W - RP_PADDING * 2,
                (GenesisConfig.showStructures ? structuresBtnY : exportBtnY) - 4);
    }

    // Nothing wraps or runs past its region: a name too long for the legend is shortened with an ellipsis.
    private String fitWidth(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
    }

    // A modded biome whose lang the client lacks would otherwise show its raw translation key.
    public static String biomeName(String key) {
        if (net.minecraft.locale.Language.getInstance().has(key)) return Component.translatable(key).getString();
        String path = BiomeColorMap.locationFromKey(key).getPath();
        StringBuilder name = new StringBuilder();
        for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private void drawCoordinateReadout(GuiGraphics gfx, int mouseX, int mouseY) {
        if (!viewport.isInBounds(mouseX, mouseY)) return;
        int bx = viewport.cursorBlockX(mouseX);
        int bz = viewport.cursorBlockZ(mouseY);
        int cx = viewport.screenToChunkX(mouseX);
        int cz = viewport.screenToChunkZ(mouseY);
        String coords = I18n.get("genesis.screen.coords", bx, bz);
        String key   = tileCache.getName(cx, cz);
        String biome = key.isEmpty() ? "" : biomeName(key);

        int tw   = Math.max(this.font.width(coords), biome.isEmpty() ? 0 : this.font.width(biome));
        int boxW = tw + 8;
        int boxH = biome.isEmpty() ? 16 : 28;
        int bx0  = 8;
        int by0  = TOP_BAR_HEIGHT + 4;

        // Dark fill
        gfx.fill(bx0, by0, bx0 + boxW, by0 + boxH, GenesisTheme.DEEP_GRAPHITE);
        // L-bracket: gold top + gold left
        gfx.fill(bx0, by0,          bx0 + boxW, by0 + 1,    GenesisTheme.FORGED_GOLD);
        gfx.fill(bx0, by0,          bx0 + 1,    by0 + boxH, GenesisTheme.FORGED_GOLD);
        // Closing corner: subtle dark bottom + dark right
        gfx.fill(bx0 + 1, by0 + boxH - 1, bx0 + boxW, by0 + boxH, GenesisTheme.HAIRLINE);
        gfx.fill(bx0 + boxW - 1, by0 + 1, bx0 + boxW, by0 + boxH, GenesisTheme.HAIRLINE);

        gfx.drawString(this.font, coords, bx0 + 4, by0 + 4, GenesisTheme.STEEL_GRAY, false);
        if (!biome.isEmpty()) {
            gfx.drawString(this.font, biome, bx0 + 4, by0 + 16, GenesisTheme.SOFT_CHAMPAGNE, false);
        }
    }

    private void drawZoomLabel(GuiGraphics gfx) {
        String zLabel = viewport.getZoom().label();
        int zW   = this.font.width(zLabel);
        int mapW = this.width - RIGHT_PANEL_W;
        int bx0  = mapW - zW - 12;
        int by0  = TOP_BAR_HEIGHT + 4;
        int bx1  = mapW - 4;
        int by1  = by0 + 16;

        // Dark fill
        gfx.fill(bx0, by0, bx1, by1, GenesisTheme.DEEP_GRAPHITE);
        // Reverse L: gold bottom + gold right
        gfx.fill(bx0, by1 - 1,   bx1,     by1,     GenesisTheme.FORGED_GOLD);
        gfx.fill(bx1 - 1, by0,   bx1,     by1,     GenesisTheme.FORGED_GOLD);
        // Opening corner: subtle dark top + dark left
        gfx.fill(bx0, by0,       bx1 - 1, by0 + 1, GenesisTheme.HAIRLINE);
        gfx.fill(bx0, by0 + 1,   bx0 + 1, by1 - 1, GenesisTheme.HAIRLINE);

        gfx.drawString(this.font, zLabel, bx0 + 4, by0 + 4, GenesisTheme.FORGED_GOLD, false);
    }

    private void drawScaleBar(GuiGraphics gfx) {
        int barBlocks = switch (viewport.getZoom()) {
            case NEAR -> 256;
            case MID  -> 512;
            case FAR  -> 1024;
            case WIDE -> 2048;
        };
        int ppc   = viewport.getZoom().pixelsPerChunk;
        int barPx = (barBlocks / 16) * ppc;
        int barX  = 12;
        int barY  = this.height - 12;

        gfx.fill(barX - 4, barY - 16, barX + barPx + 4, barY + 4, GenesisTheme.SCRIM_72);
        gfx.fill(barX, barY,     barX + barPx, barY + 1,     GenesisTheme.STEEL_GRAY);
        gfx.fill(barX, barY - 3, barX + 1,     barY + 2,     GenesisTheme.STEEL_GRAY);
        gfx.fill(barX + barPx - 1, barY - 3, barX + barPx, barY + 2, GenesisTheme.STEEL_GRAY);

        //noinspection NonStrictComparisonCanBeEquality
        String label = barBlocks >= 1024 ? I18n.get("genesis.screen.scale_k", barBlocks / 1024) : I18n.get("genesis.screen.scale", barBlocks);
        int lw = this.font.width(label);
        gfx.drawString(this.font, label, barX + (barPx - lw) / 2, barY - 12, GenesisTheme.STEEL_GRAY, false);
    }

    private void drawToast(GuiGraphics gfx) {
        if (toastUntil == 0 || System.currentTimeMillis() >= toastUntil) return;
        int rpX    = this.width - RIGHT_PANEL_W + RP_PADDING;
        int rpW    = RIGHT_PANEL_W - RP_PADDING * 2;
        String msg = fitWidth(toastText, rpW - 8);
        int mw     = this.font.width(msg);
        int mx     = rpX + (rpW - mw) / 2;
        int my     = toastY;
        // Bordered toast over the control that raised it, 4 px around the text
        gfx.fill(mx - 4, my - 4, mx + mw + 4, my + 12, GenesisTheme.DEEP_GRAPHITE);
        GenesisTheme.drawBorder(gfx, mx - 4, my - 4, mw + 8, 16, toastColor);
        gfx.drawString(this.font, msg, mx, my, toastColor, false);
    }

    private void drawUnavailableMessage(GuiGraphics gfx) {
        String msg = I18n.get(previewFailed ? "genesis.screen.failed" : "genesis.screen.unavailable");
        int mw   = this.font.width(msg);
        int mapW = this.width - RIGHT_PANEL_W;
        gfx.drawString(this.font, msg, (mapW - mw) / 2, (this.height + TOP_BAR_HEIGHT) / 2,
                GenesisTheme.STEEL_GRAY, false);
    }

    // -------------------------------------------------------------------------
    // Mod bridges (reflection - no hard compile-time dependency)
    // -------------------------------------------------------------------------

    /**
     * If GeoGradient is loaded, resets its climate state then re-initialises it
     * with the preview seed and the exact BiomeSource instance we will sample.
     * This allows GeoGradient's MultiNoiseBiomeSourceMixin to apply its
     * temperature transform during the preview, so the map reflects the
     * pole/equator gradient rather than vanilla biome distribution.
     */
    private void tryGeoGradientInit(long seed, BiomeSource source) {
        try {
            Class<?> c = Class.forName("com.alkeari.geogradient.worldgen.GeoGradientClimate");
            applyGeoGradientConfig();
            c.getMethod("reset").invoke(null);
            c.getMethod("initialize", long.class, BiomeSource.class).invoke(null, seed, source);
            // Initialize the TerraBlender bridge so the preview uses latitude-aware biome lookup.
            // Without this, mods like Tectonic register biomes with FULL_RANGE temperature in
            // TerraBlender's tree, making GeoGradient's sampler wrapping have no visible effect.
            RegistryAccess registryAccess = context.worldgenRegistries().compositeAccess();
            Class.forName("com.alkeari.geogradient.compat.terrablender.GeoGradientTerraBlenderBridge")
                 .getMethod("tryInitialize", RegistryAccess.class)
                 .invoke(null, registryAccess);
            LOGGER.info("[Genesis] GeoGradient climate initialised for preview (seed {})", seed);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[Genesis] GeoGradient bridge init failed", e);
        }
    }

    /**
     * GeoGradient copies its config into GeoGradientConfig only when an overworld loads, so on the
     * creation screen it still holds the built-in defaults; copy the user's values first, clamped as
     * GeoGradient clamps them, or the preview draws bands the world will not have.
     */
    private static void applyGeoGradientConfig() {
        String[] fields = {"globeSize", "borderAmplitude", "borderFrequency", "spawnLatitude", "blendFraction"};
        String[] keys = {"globe_size", "border_amplitude", "border_frequency", "spawn_latitude", "blend_fraction"};
        double[][] ranges = {{1000, 1000000}, {0, 10000}, {0.0001, 0.1}, {-1.0, 1.0}, {0.0, 0.5}};
        try {
            Class<?> target = Class.forName("com.alkeari.geogradient.config.GeoGradientConfig");
            Object[] values = new Object[keys.length];
            Class<?> spec = null;
            for (String name : new String[]{"neoforge.GeoGradientNeoForgeConfig", "forge.GeoGradientForgeConfig"}) {
                try {
                    spec = Class.forName("com.alkeari.geogradient.config." + name);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (spec != null) {
                for (int i = 0; i < keys.length; i++) {
                    values[i] = ((Supplier<?>) spec.getField(keys[i].toUpperCase()).get(null)).get();
                }
            } else {
                Class<?> fabric = Class.forName("com.alkeari.geogradient.config.fabric.GeoGradientFabricConfig");
                Object holder = Class.forName("me.shedaniel.autoconfig.AutoConfig").getMethod("getConfigHolder", Class.class).invoke(null, fabric);
                Object config = holder.getClass().getMethod("getConfig").invoke(holder);
                for (int i = 0; i < keys.length; i++) values[i] = fabric.getField(keys[i]).get(config);
            }
            for (int i = 0; i < fields.length; i++) {
                double value = Math.max(ranges[i][0], Math.min(ranges[i][1], ((Number) values[i]).doubleValue()));
                Field field = target.getField(fields[i]);
                if (field.getType() == int.class) field.setInt(null, (int) value);
                else field.setDouble(null, value);
            }
            LOGGER.info("[Genesis] Read GeoGradient's settings for the preview: globe size {}", target.getField("globeSize").get(null));
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[Genesis] Could not read GeoGradient's settings, so the preview uses its defaults", e);
        }
    }

    /**
     * Builds a flat list of (ParameterPoint, Holder<Biome>) pairs for the TerraBlender fallback.
     * When the live biomeSource throws (TBSampler cast, Biolith NoSuchElementException, etc.),
     * BiomePreviewThread uses this list for a plain vanilla fitness-based lookup - no TerraBlender
     * extension needed.
     *
     * Two approaches are tried in order:
     * 1. Read the parameter list directly from the biome source through its parameters field.
     *    Works when TerraBlender injects its combined parameter list into MultiNoiseBiomeSource.
     * 2. Walk terrablender.api.Regions via reflection to collect per-region biome parameters.
     *
     * Returns null if TerraBlender is not present or both approaches fail.
     */
    @SuppressWarnings("unchecked")
    private static List<Pair<Climate.ParameterPoint, Holder<Biome>>> tryBuildTerraBlenderParams(
            RegistryAccess registryAccess, BiomeSource biomeSource) {

        LOGGER.debug("[Genesis] TerraBlender params init - source class: {}", biomeSource.getClass().getName());

        // Approach 1: MultiNoiseBiomeSource's private parameters field, through the accessor mixin,
        // holds the full Climate.ParameterList<Holder<Biome>> already baked in.
        try {
            if (!(biomeSource instanceof MultiNoiseBiomeSource multiNoise)) throw new NoSuchMethodException("parameters");
            Climate.ParameterList<Holder<Biome>> paramList = parametersOf(multiNoise);
            if (paramList != null) {
                List<?> rawValues = paramList.values();
                if (rawValues != null && !rawValues.isEmpty()) {
                    List<Pair<Climate.ParameterPoint, Holder<Biome>>> result = new ArrayList<>();
                    for (Object entry : rawValues) {
                        if (entry instanceof Pair<?, ?> pair
                                && pair.getFirst() instanceof Climate.ParameterPoint point
                                && pair.getSecond() instanceof Holder<?> holder) {
                            result.add(Pair.of(point, (Holder<Biome>) holder));
                        }
                    }
                    if (!result.isEmpty()) {
                        LOGGER.info("[Genesis] TerraBlender fallback: {} entries from biome source parameters()", result.size());
                        return result;
                    }
                    LOGGER.info("[Genesis] TerraBlender: parameters() returned {} entries but none matched expected pair types - trying Regions API", rawValues.size());
                }
            }
        } catch (NoSuchMethodException ignored) {
            LOGGER.info("[Genesis] Biome source {} has no parameters() method - trying Regions API", biomeSource.getClass().getSimpleName());
        } catch (Exception e) {
            LOGGER.info("[Genesis] Biome source parameters() call failed: {} - trying Regions API", e.toString());
        }

        // Approach 2: TerraBlender Regions API via reflection.
        // Collects biome parameter points across all registered overworld regions.
        try {
            Class<?> regionsClass = Class.forName("terrablender.api.Regions");

            // TerraBlender 3.x: Regions.get(RegionType) requires a dimension type argument.
            // Resolve RegionType.OVERWORLD reflectively so we work without a compile-time dep.
            Object overworldType = null;
            try {
                Class<?> regionTypeClass = Class.forName("terrablender.api.RegionType");
                for (Object val : (Object[]) regionTypeClass.getMethod("values").invoke(null)) {
                    if ("OVERWORLD".equals(val.toString())) { overworldType = val; break; }
                }
            } catch (Exception ignored) {}

            // Accept get() (0-arg, older TB) or get(RegionType) (1-arg, TB 3.x).
            // Prefer the 1-arg form when RegionType.OVERWORLD was resolved.
            Method getMethod = null;
            for (Method m : regionsClass.getMethods()) {
                if (!m.getName().equals("get")) continue;
                if (m.getParameterCount() == 0) { getMethod = m; } // fallback
                else if (m.getParameterCount() == 1 && overworldType != null) { getMethod = m; break; }
            }
            if (getMethod == null) {
                LOGGER.warn("[Genesis] TerraBlender: no suitable Regions.get() found");
                return null;
            }

            Object regionsResult = (getMethod.getParameterCount() == 0)
                    ? getMethod.invoke(null)
                    : getMethod.invoke(null, overworldType);
            if (!(regionsResult instanceof List<?> regions) || regions.isEmpty()) {
                LOGGER.warn("[Genesis] TerraBlender: get() returned {} - no regions to process", regionsResult);
                return null;
            }
            LOGGER.debug("[Genesis] TerraBlender: {} regions found", regions.size());

            Registry<Biome> biomeReg = registryAccess.registryOrThrow(Registries.BIOME);
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> allParams = new ArrayList<>();

            for (Object region : regions) {
                // Skip non-overworld regions.
                try {
                    Object type = region.getClass().getMethod("getType").invoke(region);
                    if (type != null && !"OVERWORLD".equals(type.toString())) continue;
                } catch (Exception ignored) {}

                Method addBiomesMethod = null;
                for (Method m : region.getClass().getMethods()) {
                    if (m.getName().equals("addBiomes") && m.getParameterCount() == 2) {
                        addBiomesMethod = m;
                        break;
                    }
                }
                if (addBiomesMethod == null) {
                    LOGGER.debug("[Genesis] TerraBlender: region {} has no addBiomes(2) method", region.getClass().getSimpleName());
                    continue;
                }

                final Method finalAddBiomes = addBiomesMethod;
                try {
                    finalAddBiomes.invoke(region, biomeReg,
                        (java.util.function.Consumer<Object>) obj -> {
                            try {
                                Pair<?, ?> pair = (Pair<?, ?>) obj;
                                if (pair.getFirst() instanceof Climate.ParameterPoint point
                                        && pair.getSecond() instanceof ResourceKey<?> rawKey) {
                                    biomeReg.getHolder((ResourceKey<Biome>) rawKey)
                                            .ifPresent(h -> allParams.add(Pair.of(point, h)));
                                }
                            } catch (Exception ignored) {}
                        });
                } catch (Exception e) {
                    LOGGER.debug("[Genesis] TerraBlender: addBiomes() on {} failed: {}", region.getClass().getSimpleName(), e.toString());
                }
            }

            if (allParams.isEmpty()) {
                LOGGER.warn("[Genesis] TerraBlender: addBiomes() across {} regions yielded 0 entries - biomes may not be registered yet", regions.size());
                return null;
            }
            LOGGER.info("[Genesis] TerraBlender fallback: {} entries from {} regions via Regions API", allParams.size(), regions.size());
            return allParams;

        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            LOGGER.warn("[Genesis] TerraBlender Regions API approach failed", e);
            return null;
        }
    }

    /**
     * Resets GeoGradient's climate state so that when the actual world loads,
     * GeoGradientForge.onLevelLoad() can call initialize() with the world's
     * real BiomeSource instance and seed rather than being blocked by the
     * preview-time initialization.
     */
    private static void tryGeoGradientReset() {
        try {
            Class<?> c = Class.forName("com.alkeari.geogradient.worldgen.GeoGradientClimate");
            c.getMethod("reset").invoke(null);
            Class.forName("com.alkeari.geogradient.compat.terrablender.GeoGradientTerraBlenderBridge")
                 .getMethod("reset").invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[Genesis] GeoGradient bridge reset failed", e);
        }
    }
}
