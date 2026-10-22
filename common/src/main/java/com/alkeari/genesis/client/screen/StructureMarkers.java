package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.map.StructureLocator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/** Draws structure markers on a map and names the one under the cursor; shared by both map screens. */
final class StructureMarkers {

    private StructureMarkers() {}

    /** A hollow light square with a dark outline, apart from the player dot and the gold spawn marks. */
    static void draw(GuiGraphics gfx, MapViewport viewport, Collection<StructureLocator.Marker> markers,
                     int clipLeft, int clipTop, int clipRight, int clipBottom) {
        int ppc = viewport.getZoom().pixelsPerChunk;
        gfx.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        for (StructureLocator.Marker marker : markers) {
            int sx = viewport.chunkToScreenX(marker.chunkX()) + ppc / 2;
            int sy = viewport.chunkToScreenZ(marker.chunkZ()) + ppc / 2;
            if (sx < clipLeft - 4 || sx > clipRight + 4 || sy < clipTop - 4 || sy > clipBottom + 4) continue;
            gfx.fill(sx - 3, sy - 3, sx + 4, sy + 4, GenesisTheme.OBSIDIAN_BLACK);
            gfx.fill(sx - 2, sy - 2, sx + 3, sy + 3, GenesisTheme.SOFT_CHAMPAGNE);
            gfx.fill(sx - 1, sy - 1, sx + 2, sy + 2, GenesisTheme.OBSIDIAN_BLACK);
        }
        gfx.disableScissor();
    }

    /** Names the marker within a few pixels of the cursor, if any. */
    static void drawHover(GuiGraphics gfx, Font font, MapViewport viewport,
                          Collection<StructureLocator.Marker> markers, int mouseX, int mouseY) {
        if (!viewport.isInBounds(mouseX, mouseY)) return;
        int ppc = viewport.getZoom().pixelsPerChunk;
        for (StructureLocator.Marker marker : markers) {
            int sx = viewport.chunkToScreenX(marker.chunkX()) + ppc / 2;
            int sy = viewport.chunkToScreenZ(marker.chunkZ()) + ppc / 2;
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                gfx.renderTooltip(font, Component.translatable("genesis.structure.marker",
                        name(marker.structure()), marker.chunkX() * 16 + 8, marker.chunkZ() * 16 + 8), mouseX, mouseY);
                return;
            }
        }
    }

    /** Structures carry no display name, so the id's last path segment is shown in title case. */
    static String name(ResourceLocation id) {
        String path = id.getPath();
        StringBuilder name = new StringBuilder();
        for (String word : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
