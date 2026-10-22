package com.alkeari.genesis.client.preview;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.screen.GenesisScreen;
import net.minecraft.client.resources.language.I18n;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a chunk range of the preview to a PNG with block coordinates along its edges. */
public final class MapExporter {

    private static final int PIXELS_PER_CHUNK = 4;
    private static final int MARGIN = 48;
    private static final int GRID_BLOCKS = 512;
    private static final int LEGEND_COLUMN = 220;
    private static final int LINE = 14;

    private MapExporter() {}

    public static Path export(BiomeTileCache cache, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                              Long seed, boolean hasSpawn, int spawnBlockX, int spawnBlockZ,
                              Path folder) throws IOException {
        int chunksX = maxChunkX - minChunkX + 1;
        int chunksZ = maxChunkZ - minChunkZ + 1;
        int mapW = chunksX * PIXELS_PER_CHUNK;
        int mapH = chunksZ * PIXELS_PER_CHUNK;
        // The legend names every color in the image, unsampled and unreadable spots included, so the file
        // reads on its own; the approximate note travels with it for the same reason.
        LinkedHashMap<String, Integer> legend = new LinkedHashMap<>();
        boolean unsampled = false;
        for (int cz = minChunkZ; cz <= maxChunkZ && !unsampled; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (!cache.has(cx, cz)) { unsampled = true; break; }
            }
        }
        for (Map.Entry<String, Integer> e : cache.getVisibleBiomes(minChunkX, maxChunkX, minChunkZ, maxChunkZ).entrySet()) {
            legend.put(GenesisScreen.biomeName(e.getKey()), e.getValue());
        }
        if (unsampled) legend.put(I18n.get("genesis.export.unsampled"), GenesisTheme.UNSAMPLED_TILE);
        Font font = new Font(Font.DIALOG, Font.PLAIN, 11);
        FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(font);
        int width = mapW + MARGIN;
        List<String> note = cache.approximateNote() == null ? List.of()
                : wrap(I18n.get(cache.approximateNote()), metrics, width - 8);
        int columns = Math.max(1, (width - 4) / LEGEND_COLUMN);
        int legendRows = (legend.size() + columns - 1) / columns;
        int legendTop = mapH + MARGIN + 8;
        BufferedImage image = new BufferedImage(width, legendTop + (note.size() + legendRows) * LINE + 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(GenesisTheme.OBSIDIAN_BLACK, false));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    int argb = cache.has(cx, cz) ? cache.get(cx, cz) : GenesisTheme.UNSAMPLED_TILE;
                    g.setColor(new Color(argb, false));
                    g.fillRect(MARGIN + (cx - minChunkX) * PIXELS_PER_CHUNK, MARGIN + (cz - minChunkZ) * PIXELS_PER_CHUNK,
                            PIXELS_PER_CHUNK, PIXELS_PER_CHUNK);
                }
            }

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            Color grid = new Color(GenesisTheme.HAIRLINE, false);
            Color label = new Color(GenesisTheme.STEEL_GRAY, false);
            int minBlockX = minChunkX * 16;
            int maxBlockX = (maxChunkX + 1) * 16;
            int minBlockZ = minChunkZ * 16;
            int maxBlockZ = (maxChunkZ + 1) * 16;
            for (int bx = Math.floorDiv(minBlockX, GRID_BLOCKS) * GRID_BLOCKS + GRID_BLOCKS; bx < maxBlockX; bx += GRID_BLOCKS) {
                int x = MARGIN + (bx - minBlockX) * PIXELS_PER_CHUNK / 16;
                g.setColor(grid);
                g.drawLine(x, MARGIN, x, MARGIN + mapH - 1);
                g.setColor(label);
                String text = "x " + bx;
                g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, MARGIN - 12);
            }
            for (int bz = Math.floorDiv(minBlockZ, GRID_BLOCKS) * GRID_BLOCKS + GRID_BLOCKS; bz < maxBlockZ; bz += GRID_BLOCKS) {
                int y = MARGIN + (bz - minBlockZ) * PIXELS_PER_CHUNK / 16;
                g.setColor(grid);
                g.drawLine(MARGIN, y, MARGIN + mapW - 1, y);
                g.setColor(label);
                g.drawString("z " + bz, 4, y + 4);
            }

            // World origin, where the preview centers before any drag.
            if (minBlockX <= 0 && 0 < maxBlockX && minBlockZ <= 0 && 0 < maxBlockZ) {
                int ox = MARGIN - minBlockX * PIXELS_PER_CHUNK / 16;
                int oz = MARGIN - minBlockZ * PIXELS_PER_CHUNK / 16;
                g.setColor(new Color(GenesisTheme.FORGED_GOLD, false));
                g.setStroke(new BasicStroke(2));
                g.drawLine(ox - 8, oz, ox + 8, oz);
                g.drawLine(ox, oz - 8, ox, oz + 8);
            }

            // The spawn point chosen on the map, drawn as on screen: a gold ring around a gold dot.
            if (hasSpawn && minBlockX <= spawnBlockX && spawnBlockX < maxBlockX
                    && minBlockZ <= spawnBlockZ && spawnBlockZ < maxBlockZ) {
                int sx = MARGIN + (spawnBlockX - minBlockX) * PIXELS_PER_CHUNK / 16;
                int sz = MARGIN + (spawnBlockZ - minBlockZ) * PIXELS_PER_CHUNK / 16;
                g.setColor(new Color(GenesisTheme.FORGED_GOLD, false));
                g.setStroke(new BasicStroke(2));
                g.drawRect(sx - 6, sz - 6, 12, 12);
                g.fillRect(sx - 2, sz - 2, 4, 4);
            }

            g.setColor(new Color(GenesisTheme.FORGED_GOLD, false));
            // A client on a server never learns the seed, so its export leaves it out.
            String header = (seed == null ? "" : "Seed " + seed + "   ")
                    + (hasSpawn ? "Spawn x " + spawnBlockX + " z " + spawnBlockZ : "");
            g.drawString(header, 4, 14);

            int y = legendTop;
            g.setColor(new Color(GenesisTheme.ALERT_AMBER, false));
            for (String line : note) {
                g.drawString(line, 4, y + 10);
                y += LINE;
            }
            int i = 0;
            for (Map.Entry<String, Integer> e : legend.entrySet()) {
                int x = 4 + (i % columns) * LEGEND_COLUMN;
                int rowY = y + (i / columns) * LINE;
                g.setColor(new Color(e.getValue(), false));
                g.fillRect(x, rowY + 2, 9, 9);
                g.setColor(new Color(GenesisTheme.HAIRLINE, false));
                g.drawRect(x, rowY + 2, 9, 9);
                g.setColor(new Color(GenesisTheme.STEEL_GRAY, false));
                g.drawString(fit(e.getKey(), metrics, Math.min(LEGEND_COLUMN, width - x) - 18), x + 14, rowY + 10);
                i++;
            }
        } finally {
            g.dispose();
        }

        Files.createDirectories(folder);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        Path file = folder.resolve("genesis-map_" + (seed == null ? "server" : seed) + "_" + stamp + ".png");
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        while (!text.isEmpty() && metrics.stringWidth(text + "...") > width) text = text.substring(0, text.length() - 1);
        return text + "...";
    }

    // A word wider than the line is cut to fit, so no note line runs past the image edge.
    private static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (metrics.stringWidth(next) > width && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        lines.replaceAll(l -> fit(l, metrics, width));
        return lines;
    }
}
