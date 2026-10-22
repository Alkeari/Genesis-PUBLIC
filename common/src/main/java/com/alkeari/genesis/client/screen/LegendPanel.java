package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.preview.BiomeColorMap;
import com.alkeari.genesis.client.preview.BiomeTileCache;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The legend both maps draw: the approximate note, then every color in view, unsampled tiles included. When the
 * entries outgrow their space the mouse wheel scrolls them, so none is ever out of reach.
 */
final class LegendPanel {

    private static final int ROW = 12;
    private static final String UNSAMPLED = "genesis.export.unsampled";

    private int scroll;
    private int left, top, right, bottom;
    private boolean unsampled;
    private long unsampledCheckedAt;

    void draw(GuiGraphics gfx, Font font, BiomeTileCache cache, MapViewport viewport, int x, int y, int width, int bottomLimit) {
        left = x;
        top = y;
        right = x + width;
        bottom = bottomLimit;

        String note = cache.approximateNote();
        if (note != null) {
            for (var line : font.split(Component.translatable(note), width)) {
                gfx.drawString(font, line, x, y, GenesisTheme.ALERT_AMBER, false);
                y += 10;
            }
            y += 4;
        }

        int minX = viewport.visibleMinChunkX(), maxX = viewport.visibleMaxChunkX();
        int minZ = viewport.visibleMinChunkZ(), maxZ = viewport.visibleMaxChunkZ();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(cache.getVisibleBiomes(minX, maxX, minZ, maxZ).entrySet());
        if (entries.isEmpty()) {
            gfx.drawString(font, I18n.get("genesis.screen.sampling"), x, y + 1, GenesisTheme.STEEL_GRAY, false);
            return;
        }
        // Scanning every tile in view each frame is costly at the widest zoom, and the answer changes slowly.
        long now = System.currentTimeMillis();
        if (now - unsampledCheckedAt > 500) {
            unsampledCheckedAt = now;
            unsampled = anyUnsampled(cache, minX, maxX, minZ, maxZ);
        }
        if (unsampled) entries.add(Map.entry(UNSAMPLED, GenesisTheme.UNSAMPLED_TILE));

        int capacity = Math.max(1, (bottomLimit - y) / ROW);
        boolean overflow = entries.size() > capacity;
        int rows = overflow ? Math.max(1, capacity - 1) : capacity;
        scroll = Math.max(0, Math.min(scroll, entries.size() - rows));
        for (int i = scroll; i < Math.min(entries.size(), scroll + rows); i++) {
            String key = entries.get(i).getKey();
            int border = key.equals(UNSAMPLED) ? GenesisTheme.HAIRLINE : BiomeColorMap.getBorderColor(BiomeColorMap.locationFromKey(key));
            gfx.fill(x, y, x + 8, y + 8, border);
            gfx.fill(x + 1, y + 1, x + 7, y + 7, entries.get(i).getValue());
            gfx.drawString(font, fitWidth(font, GenesisScreen.biomeName(key), width - 12), x + 12, y, GenesisTheme.STEEL_GRAY, false);
            y += ROW;
        }
        if (overflow) {
            int below = entries.size() - scroll - rows;
            gfx.drawString(font, fitWidth(font, I18n.get("genesis.screen.legend.more", scroll, below), width), x, y,
                    GenesisTheme.DISABLED_TEXT, false);
        }
    }

    /** Scrolls the entries when the wheel turns over the legend; false when the pointer is elsewhere. */
    boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) return false;
        scroll -= (int) Math.signum(amount);
        return true;
    }

    private static boolean anyUnsampled(BiomeTileCache cache, int minX, int maxX, int minZ, int maxZ) {
        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (!cache.has(cx, cz)) return true;
            }
        }
        return false;
    }

    private static String fitWidth(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
