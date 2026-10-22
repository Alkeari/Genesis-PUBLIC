package com.alkeari.genesis.client;

import net.minecraft.client.gui.GuiGraphics;

public final class GenesisTheme {

    // Backgrounds
    public static final int OBSIDIAN_BLACK  = 0xFF000000;
    public static final int DEEP_GRAPHITE   = 0xFF1A1A1A;

    // Text / labels
    public static final int STEEL_GRAY      = 0xFFA6A6A6;
    public static final int SOFT_CHAMPAGNE  = 0xFFFFF1B3;

    // Primary accent
    public static final int FORGED_GOLD     = 0xFFFFDA00;
    public static final int WARM_GOLD_SHADOW = 0xFFC9A800;

    // Warning and invalid input, the one semantic accent this screen uses beside gold.
    public static final int ALERT_AMBER        = 0xFFFFB000;

    // Neutrals (design system section 2.1): hueless and structural, never accents.
    public static final int HAIRLINE        = 0xFF333333;
    public static final int RAISED_FILL     = 0xFF2E2E2E;
    public static final int RAISED_HOVER    = 0xFF3D3D3D;
    public static final int DISABLED_TEXT   = 0xFF6E6E6E;

    // Scrim: Obsidian Black at 72 percent. The palette also allows 60 percent; add it when a screen
    // needs it rather than carrying a constant nothing draws with.
    public static final int SCRIM_72        = 0xB8000000;

    // Unsampled tile placeholder: the region fill, so a tile with no sample reads as empty map
    // rather than as a near-black invention outside the palette.
    public static final int UNSAMPLED_TILE  = DEEP_GRAPHITE;

    // The design system's one transition duration, for hover, press and state changes.
    public static final long TRANSITION_NANOS = 160_000_000L;

    public static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Keyboard focus: a 2 px Forged Gold ring 2 px outside the element. */
    public static void drawFocusRing(GuiGraphics gfx, int x, int y, int w, int h) {
        drawBorder(gfx, x - 4, y - 4, w + 8, h + 8, 2, FORGED_GOLD);
    }

    public static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int thickness, int color) {
        gfx.fill(x,             y,             x + w, y + thickness, color);
        gfx.fill(x,             y + h - thickness, x + w, y + h,     color);
        gfx.fill(x,             y,             x + thickness, y + h, color);
        gfx.fill(x + w - thickness, y,         x + w, y + h,         color);
    }

    public static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        drawBorder(gfx, x, y, w, h, 1, color);
    }

    private GenesisTheme() {}
}
