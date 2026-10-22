package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.preview.BiomeTileCache;
import net.minecraft.client.gui.GuiGraphics;

public final class MapViewport {

    // Panel bounds (set each frame from GenesisScreen layout)
    private int mapLeft, mapTop, mapWidth, mapHeight;

    // View state (in chunk coordinates)
    private float viewCenterX = 0f;
    private float viewCenterZ = 0f;
    private ZoomLevel zoom = ZoomLevel.MID;
    private int crosshairChunkX = 0;
    private int crosshairChunkZ = 0;

    // Drag tracking
    private boolean dragging = false;
    private double dragStartMouseX, dragStartMouseY;
    private float dragStartCenterX, dragStartCenterZ;

    public void setBounds(int left, int top, int width, int height) {
        this.mapLeft = left;
        this.mapTop = top;
        this.mapWidth = width;
        this.mapHeight = height;
    }

    // -------------------------------------------------------------------------
    // Coordinate transforms
    // -------------------------------------------------------------------------

    /** Converts a chunk coordinate to the screen X pixel at that chunk's left edge. */
    public int chunkToScreenX(int chunkX) {
        return mapLeft + mapWidth / 2 + (int) ((chunkX - viewCenterX) * zoom.pixelsPerChunk);
    }

    /** Converts a chunk coordinate to the screen Y pixel at that chunk's top edge. */
    public int chunkToScreenZ(int chunkZ) {
        return mapTop + mapHeight / 2 + (int) ((chunkZ - viewCenterZ) * zoom.pixelsPerChunk);
    }

    /** Converts a screen X pixel to the chunk column it falls within. */
    public int screenToChunkX(double screenX) {
        return (int) Math.floor((screenX - mapLeft - mapWidth / 2.0) / zoom.pixelsPerChunk + viewCenterX);
    }

    /** Converts a screen Y pixel to the chunk row it falls within. */
    public int screenToChunkZ(double screenY) {
        return (int) Math.floor((screenY - mapTop - mapHeight / 2.0) / zoom.pixelsPerChunk + viewCenterZ);
    }

    /** Returns true if the given screen coordinate is inside the map panel. */
    public boolean isInBounds(double screenX, double screenY) {
        return screenX >= mapLeft && screenX < mapLeft + mapWidth
            && screenY >= mapTop  && screenY < mapTop  + mapHeight;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(GuiGraphics gfx, BiomeTileCache cache) {
        int ppc = zoom.pixelsPerChunk;

        // Compute visible chunk range
        int minChunkX = screenToChunkX(mapLeft)     - 1;
        int maxChunkX = screenToChunkX(mapLeft + mapWidth)  + 1;
        int minChunkZ = screenToChunkZ(mapTop)      - 1;
        int maxChunkZ = screenToChunkZ(mapTop + mapHeight)  + 1;

        // Clip rendering to the map panel
        gfx.enableScissor(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight);

        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                int sx     = chunkToScreenX(cx);
                int sy     = chunkToScreenZ(cz);
                int fill   = cache.get(cx, cz);
                int border = cache.getBorderColor(cx, cz);
                // Outer tile: unique biome border color
                gfx.fill(sx, sy, sx + ppc, sy + ppc, border);
                // Inner fill: category color (only has room at ppc > 2)
                if (ppc > 2) {
                    gfx.fill(sx + 1, sy + 1, sx + ppc - 1, sy + ppc - 1, fill);
                }
            }
        }

        // Spawn crosshair at chunk (0, 0)
        drawSpawnCrosshair(gfx);

        gfx.disableScissor();
    }

    private void drawSpawnCrosshair(GuiGraphics gfx) {
        int gold = GenesisTheme.FORGED_GOLD;
        int cx = chunkToScreenX(crosshairChunkX) + zoom.pixelsPerChunk / 2;
        int cz = chunkToScreenZ(crosshairChunkZ) + zoom.pixelsPerChunk / 2;

        int armLen = 10;
        int gap    = 3;

        // Horizontal lines (left + right of center)
        gfx.fill(cx - armLen - gap, cz,     cx - gap,          cz + 1, gold);
        gfx.fill(cx + gap,          cz,     cx + armLen + gap, cz + 1, gold);
        // Vertical lines (above + below center)
        gfx.fill(cx,     cz - armLen - gap, cx + 1, cz - gap,          gold);
        gfx.fill(cx,     cz + gap,          cx + 1, cz + armLen + gap, gold);

        // Center dot
        gfx.fill(cx - 1, cz - 1, cx + 2, cz + 2, gold);
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    public void onMousePressed(double mouseX, double mouseY, int button) {
        if (button == 0 && isInBounds(mouseX, mouseY)) {
            dragging = true;
            dragStartMouseX  = mouseX;
            dragStartMouseY  = mouseY;
            dragStartCenterX = viewCenterX;
            dragStartCenterZ = viewCenterZ;
        }
    }

    @SuppressWarnings("unused")
    public void onMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
    }

    public void onMouseDragged(double mouseX, double mouseY) {
        if (!dragging) return;
        double dx = mouseX - dragStartMouseX;
        double dy = mouseY - dragStartMouseY;
        viewCenterX = dragStartCenterX - (float)(dx / zoom.pixelsPerChunk);
        viewCenterZ = dragStartCenterZ - (float)(dy / zoom.pixelsPerChunk);
    }

    public void onScroll(double mouseX, double mouseY, double delta) {
        if (!isInBounds(mouseX, mouseY)) return;
        zoom = zoom.scroll(delta);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ZoomLevel getZoom()       { return zoom; }
    public void setZoom(ZoomLevel z) { this.zoom = z; }
    @SuppressWarnings("unused") public float getViewCenterX() { return viewCenterX; }
    @SuppressWarnings("unused") public float getViewCenterZ() { return viewCenterZ; }
    @SuppressWarnings("unused") public void resetToSpawn()    { viewCenterX = 0f; viewCenterZ = 0f; }
    public void setCenter(float chunkX, float chunkZ) { viewCenterX = chunkX; viewCenterZ = chunkZ; }

    /** Moves the gold crosshair from the world origin to the given chunk, such as a running world's spawn. */
    public void setCrosshairChunk(int chunkX, int chunkZ) { crosshairChunkX = chunkX; crosshairChunkZ = chunkZ; }

    public int visibleMinChunkX() { return screenToChunkX(mapLeft)             - 1; }
    public int visibleMaxChunkX() { return screenToChunkX(mapLeft + mapWidth)  + 1; }
    public int visibleMinChunkZ() { return screenToChunkZ(mapTop)              - 1; }
    public int visibleMaxChunkZ() { return screenToChunkZ(mapTop + mapHeight)  + 1; }

    /** Returns the block X coordinate under the mouse cursor. */
    public int cursorBlockX(double mouseX) {
        return screenToChunkX(mouseX) * 16;
    }

    /** Returns the block Z coordinate under the mouse cursor. */
    public int cursorBlockZ(double mouseY) {
        return screenToChunkZ(mouseY) * 16;
    }
}
