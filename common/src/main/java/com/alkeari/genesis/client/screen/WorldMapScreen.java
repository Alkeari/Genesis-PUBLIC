package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.preview.BiomePreviewThread;
import com.alkeari.genesis.client.preview.BiomeTileCache;
import com.alkeari.genesis.client.preview.MapExporter;
import com.alkeari.genesis.config.GenesisConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alkeari.genesis.client.network.GenesisNetwork;
import com.alkeari.genesis.map.StructureLocator;
import com.alkeari.genesis.map.WorldMapService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The biome map of the world being played, opened in game with the world map key. */
public final class WorldMapScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/WorldMapScreen");

    private static final int TOP_BAR_HEIGHT = 28;
    private static final int RIGHT_PANEL_W  = 160;
    private static final int RP_PADDING     = 8;

    // Right-panel layout (relative to screen top), on the design system's 4 px grid
    private static final int RP_ZOOM_LABEL_Y   = TOP_BAR_HEIGHT + 8;
    private static final int RP_ZOOM_BTN_Y     = TOP_BAR_HEIGHT + 20;
    private static final int RP_DIVIDER_Y      = TOP_BAR_HEIGHT + 40;
    private static final int RP_LEGEND_LABEL_Y = TOP_BAR_HEIGHT + 48;
    private static final int RP_LEGEND_Y       = TOP_BAR_HEIGHT + 60;

    private final BiomeTileCache tileCache = new BiomeTileCache();
    private final MapViewport viewport     = new MapViewport();
    private final LegendPanel legend       = new LegendPanel();

    // The connection whose server said it answers world map requests; any other connection has not.
    private static volatile Object serverMapConnection;

    private BiomePreviewThread[] threads = new BiomePreviewThread[0];
    private boolean unavailable = false;
    private Long seed;

    // Server mode: regions asked for (packed region coordinates) and how many are still unanswered.
    private boolean fromServer = false;
    private ResourceLocation dimension;
    private final Set<Long> requested = new HashSet<>();
    private int inFlight = 0;

    // Structure markers: found here in singleplayer, sent with each region on a server.
    private StructureLocator structureLocator;
    private final java.util.Set<StructureLocator.Marker> serverStructures = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean structuresShown = true;
    private int structuresBtnY;
    private int exportBtnY;
    private long toastUntil = 0;
    private String toastText = "";
    private int toastColor;

    public WorldMapScreen() {
        super(Component.translatable("genesis.map.title"));
    }

    @Override
    protected void init() {
        int mapW = this.width - RIGHT_PANEL_W;
        viewport.setBounds(0, TOP_BAR_HEIGHT, mapW, this.height - TOP_BAR_HEIGHT);

        int rpX = this.width - RIGHT_PANEL_W + RP_PADDING;
        int rpW = RIGHT_PANEL_W - RP_PADDING * 2;

        addRenderableWidget(GenesisButton.of(4, 4, 56, 20, "genesis.map.close", this::onClose)
                .tip("genesis.map.close.tip"));

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

        int centerBtnY = this.height - RP_PADDING - 16;
        exportBtnY     = centerBtnY - 4 - 16;
        structuresBtnY = exportBtnY - 4 - 16;
        if (GenesisConfig.showStructures) {
            addRenderableWidget(GenesisButton.toggle(rpX, structuresBtnY, rpW, 16, "genesis.structure.toggle",
                    () -> structuresShown = !structuresShown, () -> structuresShown)
                    .tip("genesis.structure.toggle.tip"));
        }
        addRenderableWidget(GenesisButton.of(rpX, exportBtnY, rpW, 16, "genesis.screen.export", this::exportMap)
                .tip("genesis.screen.export.tip"));
        addRenderableWidget(GenesisButton.of(rpX, centerBtnY, rpW, 16, "genesis.map.center", this::centerOnPlayer)
                .tip("genesis.map.center.tip"));

        if (threads.length == 0 && !unavailable && !fromServer) {
            centerOnPlayer();
            startSampling();
        }
    }

    private void centerOnPlayer() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        BlockPos pos = this.minecraft.player.blockPosition();
        viewport.setCenter(pos.getX() / 16f, pos.getZ() / 16f);
    }

    public static void onServerMapAvailable(boolean available) {
        serverMapConnection = available ? Minecraft.getInstance().getConnection() : null;
    }

    /**
     * Samples the running world's own generator, which already holds every change biome mods made to it.
     * In singleplayer, or when hosting, that generator is in this game; on a server running Genesis the
     * server samples it and sends regions back, since a client never learns the seed.
     */
    private void startSampling() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            unavailable = true;
            return;
        }
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            Object connection = this.minecraft.getConnection();
            if (connection == null || connection != serverMapConnection) {
                unavailable = true;
                return;
            }
            fromServer = true;
            dimension  = this.minecraft.level.dimension().location();
            BlockPos spawn = this.minecraft.level.getSharedSpawnPos();
            viewport.setCrosshairChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
            LOGGER.info("[Genesis] World map asking the server for {}", dimension);
            return;
        }
        try {
            ServerLevel level = server.getLevel(this.minecraft.level.dimension());
            if (level == null) { unavailable = true; return; }
            seed = level.getSeed();
            var chunkSource = level.getChunkSource();
            var source      = chunkSource.getGenerator().getBiomeSource();
            var sampler     = chunkSource.randomState().sampler();
            var registry    = level.registryAccess().registryOrThrow(Registries.BIOME);

            BlockPos spawn = level.getSharedSpawnPos();
            viewport.setCrosshairChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
            BlockPos pos = this.minecraft.player.blockPosition();

            int count = GenesisConfig.previewThreads > 0
                    ? GenesisConfig.previewThreads
                    : Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            threads = new BiomePreviewThread[count];
            for (int i = 0; i < count; i++) {
                threads[i] = new BiomePreviewThread(source, sampler, tileCache, i, count, registry, null,
                        () -> viewport.screenToChunkX(0) - 2,
                        () -> viewport.screenToChunkX(this.width - RIGHT_PANEL_W) + 2,
                        () -> viewport.screenToChunkZ(TOP_BAR_HEIGHT) - 2,
                        () -> viewport.screenToChunkZ(this.height) + 2)
                        .forLiveWorld(pos.getX() >> 4, pos.getZ() >> 4);
                threads[i].start();
            }
            if (GenesisConfig.showStructures) {
                structureLocator = new StructureLocator(chunkSource.getGeneratorState(), source, sampler,
                        () -> viewport.visibleMinChunkX(), () -> viewport.visibleMaxChunkX(),
                        () -> viewport.visibleMinChunkZ(), () -> viewport.visibleMaxChunkZ(),
                        StructureLocator.exact(level));
                structureLocator.start();
            }
            LOGGER.info("[Genesis] World map sampling {} with {} threads", level.dimension().location(), count);
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to start the world map", t);
            unavailable = true;
        }
    }

    @Override
    public void tick() {
        if (fromServer) requestVisibleRegions();
    }

    /** Asks for the unrequested regions in view, nearest to the map's center first, within the server's limit. */
    private void requestVisibleRegions() {
        int size   = WorldMapService.REGION;
        int minRX  = Math.floorDiv(viewport.visibleMinChunkX(), size);
        int maxRX  = Math.floorDiv(viewport.visibleMaxChunkX(), size);
        int minRZ  = Math.floorDiv(viewport.visibleMinChunkZ(), size);
        int maxRZ  = Math.floorDiv(viewport.visibleMaxChunkZ(), size);
        float centerRX = (minRX + maxRX) / 2f;
        float centerRZ = (minRZ + maxRZ) / 2f;
        List<long[]> wanted = new ArrayList<>();
        for (int rz = minRZ; rz <= maxRZ; rz++) {
            for (int rx = minRX; rx <= maxRX; rx++) {
                if (!requested.contains(packRegion(rx, rz))) wanted.add(new long[]{rx, rz});
            }
        }
        wanted.sort(Comparator.comparingDouble(r -> Math.hypot(r[0] - centerRX, r[1] - centerRZ)));
        for (long[] r : wanted) {
            if (inFlight >= WorldMapService.MAX_PENDING) return;
            requested.add(packRegion((int) r[0], (int) r[1]));
            inFlight++;
            GenesisNetwork.sendMapRequest((int) r[0] * size, (int) r[1] * size);
        }
    }

    private static long packRegion(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    /** Fills the tiles of one region the server sampled. */
    public void onRegion(WorldMapService.Region region) {
        if (!fromServer) return;
        inFlight = Math.max(0, inFlight - 1);
        if (!region.dimension().equals(dimension) || this.minecraft == null || this.minecraft.level == null) return;
        Registry<Biome> biomes = this.minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
        int size = WorldMapService.REGION;
        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int cx = region.minChunkX() + dx;
                int cz = region.minChunkZ() + dz;
                short index = region.indices()[dz * size + dx];
                if (index < 0 || index >= region.palette().size()) {
                    tileCache.put(cx, cz, GenesisTheme.STEEL_GRAY, GenesisTheme.HAIRLINE, BiomePreviewThread.UNREADABLE);
                } else {
                    ResourceLocation loc = region.palette().get(index);
                    BiomePreviewThread.putBiome(tileCache, cx, cz, loc, biomes.get(loc));
                }
            }
        }
        for (StructureLocator.Marker marker : region.structures()) {
            serverStructures.add(marker);
        }
    }

    private java.util.Collection<StructureLocator.Marker> structureMarkers() {
        return fromServer ? serverStructures : structureLocator == null ? List.of() : structureLocator.markers();
    }

    private void stopSampling() {
        for (BiomePreviewThread t : threads) t.interrupt();
        threads = new BiomePreviewThread[0];
        if (structureLocator != null) structureLocator.interrupt();
    }

    @Override
    public void onClose() {
        stopSampling();
        super.onClose();
    }

    @Override
    public void removed() {
        stopSampling();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.alkeari.genesis.client.GenesisKeys.WORLD_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    private void exportMap() {
        if (this.minecraft == null || unavailable) return;
        try {
            java.nio.file.Path file = MapExporter.export(tileCache,
                    viewport.visibleMinChunkX(), viewport.visibleMaxChunkX(),
                    viewport.visibleMinChunkZ(), viewport.visibleMaxChunkZ(),
                    seed, false, 0, 0,
                    this.minecraft.gameDirectory.toPath().resolve("screenshots"));
            LOGGER.info("[Genesis] Exported the world map to {}", file);
            showToast(I18n.get("genesis.screen.exported"), GenesisTheme.FORGED_GOLD);
        } catch (Exception e) {
            LOGGER.error("[Genesis] Could not export the world map", e);
            showToast(I18n.get("genesis.screen.export_failed"), GenesisTheme.ALERT_AMBER);
        }
    }

    private void showToast(String text, int color) {
        toastText  = text;
        toastColor = color;
        toastUntil = System.currentTimeMillis() + 2000;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // The map covers the whole screen; the default blur or dirt would only be drawn over.
    }
    //?}

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, this.width, this.height, GenesisTheme.OBSIDIAN_BLACK);
        drawTopBar(gfx);

        int mapW = this.width - RIGHT_PANEL_W;
        if (unavailable) {
            String msg = I18n.get("genesis.map.unavailable");
            gfx.drawString(this.font, fitWidth(msg, mapW - 16), 8, (this.height + TOP_BAR_HEIGHT) / 2,
                    GenesisTheme.STEEL_GRAY, false);
        } else {
            viewport.render(gfx, tileCache);
            if (structuresShown && GenesisConfig.showStructures) {
                StructureMarkers.draw(gfx, viewport, structureMarkers(), 0, TOP_BAR_HEIGHT, mapW, this.height);
            }
            drawPlayer(gfx);
            drawReadout(gfx, mouseX, mouseY);
        }

        gfx.fill(mapW, TOP_BAR_HEIGHT, mapW + 1, this.height, GenesisTheme.FORGED_GOLD);
        drawRightPanel(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);
        if (!unavailable && structuresShown && GenesisConfig.showStructures) {
            StructureMarkers.drawHover(gfx, this.font, viewport, structureMarkers(), mouseX, mouseY);
        }
        drawToast(gfx);
    }

    private void drawTopBar(GuiGraphics gfx) {
        gfx.fill(0, 0, this.width, TOP_BAR_HEIGHT, GenesisTheme.DEEP_GRAPHITE);
        gfx.fill(0, TOP_BAR_HEIGHT - 1, this.width, TOP_BAR_HEIGHT, GenesisTheme.FORGED_GOLD);
        String title = I18n.get("genesis.map.banner");
        int x = (this.width - this.font.width(title)) / 2;
        int y = (TOP_BAR_HEIGHT - 8) / 2;
        gfx.drawString(this.font, title, x + 1, y + 1, GenesisTheme.WARM_GOLD_SHADOW, false);
        gfx.drawString(this.font, title, x, y, GenesisTheme.FORGED_GOLD, false);
    }

    /** A light dot with a dark outline where the player stands, apart from the gold spawn crosshair. */
    private void drawPlayer(GuiGraphics gfx) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        BlockPos pos = this.minecraft.player.blockPosition();
        int ppc = viewport.getZoom().pixelsPerChunk;
        int sx = viewport.chunkToScreenX(pos.getX() >> 4) + ppc / 2;
        int sy = viewport.chunkToScreenZ(pos.getZ() >> 4) + ppc / 2;
        gfx.enableScissor(0, TOP_BAR_HEIGHT, this.width - RIGHT_PANEL_W, this.height);
        gfx.fill(sx - 3, sy - 3, sx + 4, sy + 4, GenesisTheme.OBSIDIAN_BLACK);
        gfx.fill(sx - 2, sy - 2, sx + 3, sy + 3, GenesisTheme.SOFT_CHAMPAGNE);
        gfx.disableScissor();
    }

    private void drawReadout(GuiGraphics gfx, int mouseX, int mouseY) {
        if (!viewport.isInBounds(mouseX, mouseY)) return;
        String coords = I18n.get("genesis.screen.coords", viewport.cursorBlockX(mouseX), viewport.cursorBlockZ(mouseY));
        String key    = tileCache.getName(viewport.screenToChunkX(mouseX), viewport.screenToChunkZ(mouseY));
        String biome  = key.isEmpty() ? "" : GenesisScreen.biomeName(key);
        int boxW = Math.max(this.font.width(coords), this.font.width(biome)) + 8;
        int boxH = biome.isEmpty() ? 16 : 28;
        int x = 8;
        int y = TOP_BAR_HEIGHT + 4;
        gfx.fill(x, y, x + boxW, y + boxH, GenesisTheme.DEEP_GRAPHITE);
        gfx.fill(x, y, x + boxW, y + 1, GenesisTheme.FORGED_GOLD);
        gfx.fill(x, y, x + 1, y + boxH, GenesisTheme.FORGED_GOLD);
        gfx.drawString(this.font, coords, x + 4, y + 4, GenesisTheme.STEEL_GRAY, false);
        if (!biome.isEmpty()) gfx.drawString(this.font, biome, x + 4, y + 16, GenesisTheme.SOFT_CHAMPAGNE, false);
    }

    private void drawRightPanel(GuiGraphics gfx) {
        int rpX   = this.width - RIGHT_PANEL_W;
        int inner = rpX + RP_PADDING;
        int rpW   = RIGHT_PANEL_W - RP_PADDING * 2;
        gfx.fill(rpX + 1, TOP_BAR_HEIGHT, this.width, this.height, GenesisTheme.DEEP_GRAPHITE);

        drawSectionLabel(gfx, inner, RP_ZOOM_LABEL_Y, I18n.get("genesis.screen.zoom"));
        gfx.fill(inner, RP_DIVIDER_Y, inner + rpW, RP_DIVIDER_Y + 1, GenesisTheme.HAIRLINE);
        drawSectionLabel(gfx, inner, RP_LEGEND_LABEL_Y, I18n.get("genesis.screen.legend"));

        legend.draw(gfx, this.font, tileCache, viewport, inner, RP_LEGEND_Y, rpW,
                (GenesisConfig.showStructures ? structuresBtnY : exportBtnY) - 4);
    }

    private void drawSectionLabel(GuiGraphics gfx, int x, int y, String label) {
        gfx.fill(x, y, x + 2, y + 8, GenesisTheme.FORGED_GOLD);
        gfx.drawString(this.font, label, x + 8, y, GenesisTheme.STEEL_GRAY, false);
    }

    private void drawToast(GuiGraphics gfx) {
        if (toastUntil == 0 || System.currentTimeMillis() >= toastUntil) return;
        int rpX = this.width - RIGHT_PANEL_W + RP_PADDING;
        int rpW = RIGHT_PANEL_W - RP_PADDING * 2;
        String msg = fitWidth(toastText, rpW - 8);
        int mw = this.font.width(msg);
        int mx = rpX + (rpW - mw) / 2;
        int my = exportBtnY + 4;
        gfx.fill(mx - 4, my - 4, mx + mw + 4, my + 12, GenesisTheme.DEEP_GRAPHITE);
        GenesisTheme.drawBorder(gfx, mx - 4, my - 4, mw + 8, 16, toastColor);
        gfx.drawString(this.font, msg, mx, my, toastColor, false);
    }

    private String fitWidth(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
    }
}
