package com.alkeari.genesis.client.screen;

import net.minecraft.client.resources.language.I18n;
import com.alkeari.genesis.client.GenesisTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight in-game HUD overlay for command-driven pre-gen progress.
 * Shown at the top of the screen whenever a pre-gen job is running and
 * PreGenScreen is not open. Updated by GenesisNetwork's S2C handler.
 */
public final class PreGenHud {

    private static volatile boolean running        = false;
    private static volatile String  dimText        = "";
    private static volatile int     completed      = 0;
    private static volatile int     total          = 1;
    private static volatile int     queueRemaining = 0;
    private static volatile boolean paused         = false;

    // Rolling average for ETA: ring buffer of (timestamp ms, completed count)
    private static final long[] etaTs   = new long[10];
    private static final int[]  etaComp = new int[10];
    private static int etaHead  = 0;
    private static int etaCount = 0;

    private PreGenHud() {}

    /** Called from GenesisNetwork S2C handler on the main client thread. */
    public static void update(ResourceLocation dim, int comp, int tot,
                               int queueRem, boolean isPaused, boolean done) {
        dimText        = dim.toString();
        queueRemaining = queueRem;
        paused         = isPaused;

        if (done && queueRem == 0) {
            running   = false;
            completed = comp;
            total     = tot;
            return;
        }

        if (done) {
            // Next dimension starting - reset ETA samples
            completed = 0;
            total     = tot;
            etaCount  = 0;
        } else {
            completed = comp;
            total     = tot;
        }

        running = true;

        // A paused interval says nothing about speed, so the samples restart once it resumes.
        if (isPaused) {
            etaCount = 0;
            return;
        }

        // Record ETA sample
        etaTs[etaHead]   = System.currentTimeMillis();
        etaComp[etaHead] = comp;
        etaHead          = (etaHead + 1) % 10;
        if (etaCount < 10) etaCount++;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isActive() {
        return running;
    }

    /** Call when connecting to a new world or on explicit cancel to clear stale HUD state. */
    public static void reset() {
        running        = false;
        dimText        = "";
        completed      = 0;
        total          = 1;
        queueRemaining = 0;
        paused         = false;
        etaHead        = 0;
        etaCount       = 0;
    }

    @SuppressWarnings("unused")
    public static void render(GuiGraphics gfx, Font font, int screenW, int screenH) {
        if (!running) return;

        double pct   = total > 0 ? (100.0 * completed / total) : 0.0;
        String queue = queueRemaining > 0 ? "  " + I18n.get("genesis.pregen.queued", queueRemaining) : "";
        String icon  = paused ? "⏸ " : "⟳ ";  // ⏸ or ⟳
        String line1 = icon + dimText + "  " +
                String.format("%,d / %,d  (%.1f%%)", completed, total, pct) +
                queue;
        String eta   = computeEta();
        boolean hasEta = !eta.isEmpty();

        // Layout
        int barW   = Math.min(screenW * 6 / 10, 400);
        int barH   = 6;
        int padX   = 12;
        int padY   = 4;
        int textH  = font.lineHeight;
        int panelW = barW + padX * 2;
        int panelH = padY + textH + (hasEta ? textH + 2 : 0) + 4 + barH + padY;
        int panelX = (screenW - panelW) / 2;
        int panelY = 2;

        // Background + gold top border
        gfx.fill(panelX,     panelY,     panelX + panelW, panelY + panelH, GenesisTheme.DEEP_GRAPHITE);
        gfx.fill(panelX,     panelY,     panelX + panelW, panelY + 1,      GenesisTheme.FORGED_GOLD);
        gfx.fill(panelX,     panelY + panelH - 1, panelX + panelW, panelY + panelH, GenesisTheme.HAIRLINE);
        gfx.fill(panelX,     panelY,     panelX + 1,      panelY + panelH, GenesisTheme.HAIRLINE);
        gfx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, GenesisTheme.HAIRLINE);

        // Status text (centered)
        int textColor = paused ? GenesisTheme.FORGED_GOLD : GenesisTheme.STEEL_GRAY;
        int line1X    = (screenW - font.width(line1)) / 2;
        gfx.drawString(font, line1, line1X, panelY + padY, textColor, false);

        int nextY = panelY + padY + textH;

        if (hasEta) {
            int etaX = (screenW - font.width(eta)) / 2;
            gfx.drawString(font, eta, etaX, nextY + 2, GenesisTheme.STEEL_GRAY, false);
            nextY += textH + 2;
        }

        // Progress bar
        int barX    = (screenW - barW) / 2;
        int barY    = nextY + 4;
        int filled  = total > 0 ? (int)((long) barW * completed / total) : 0;
        int barBg   = GenesisTheme.RAISED_FILL;
        gfx.fill(barX, barY, barX + barW, barY + barH, barBg);
        if (filled > 0) {
            gfx.fill(barX, barY, barX + filled, barY + barH,
                    paused ? GenesisTheme.ALERT_AMBER : GenesisTheme.FORGED_GOLD);
        }
    }

    private static String computeEta() {
        if (etaCount < 2) return "";
        // Find oldest and newest entries
        int  oldestIdx = (etaHead - etaCount + 10) % 10;
        int  newestIdx = (etaHead - 1 + 10) % 10;
        long dtMs      = etaTs[newestIdx]   - etaTs[oldestIdx];
        int  dtComp    = etaComp[newestIdx] - etaComp[oldestIdx];
        if (dtMs <= 0 || dtComp <= 0) return "";
        double chunksPerMs = (double) dtComp / dtMs;
        long   remMs       = (long)((total - completed) / chunksPerMs);
        long   seconds     = remMs / 1000;
        if (seconds < 5)  return "";
        if (seconds < 60) return I18n.get("genesis.pregen.eta_seconds", seconds);
        return I18n.get("genesis.pregen.eta_minutes", seconds / 60);
    }
}
