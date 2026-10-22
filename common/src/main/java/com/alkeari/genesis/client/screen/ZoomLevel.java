package com.alkeari.genesis.client.screen;

import net.minecraft.client.resources.language.I18n;

public enum ZoomLevel {
    NEAR(8),
    MID(4),
    FAR(2),
    WIDE(1);

    public final int pixelsPerChunk;

    ZoomLevel(int pixelsPerChunk) {
        this.pixelsPerChunk = pixelsPerChunk;
    }

    /** Returns the next more-zoomed-in level, or this level if already at NEAR. */
    public ZoomLevel zoomIn() {
        return switch (this) {
            case WIDE -> FAR;
            case FAR -> MID;
            case MID, NEAR -> NEAR;
        };
    }

    /** Returns the next more-zoomed-out level, or this level if already at WIDE. */
    public ZoomLevel zoomOut() {
        return switch (this) {
            case NEAR -> MID;
            case MID -> FAR;
            case FAR, WIDE -> WIDE;
        };
    }

    /** Cycles zoom level on scroll: positive delta = zoom in, negative = zoom out. */
    public ZoomLevel scroll(double delta) {
        return delta > 0 ? zoomIn() : zoomOut();
    }

    public String label() {
        return I18n.get("genesis.zoom." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
