package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.network.GenesisNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;

public final class PreGenScreen extends Screen {

    private String dimText         = "...";
    private int    completedChunks = 0;
    private int    totalChunks     = 1;
    private boolean paused         = false;

    private int  currentJobIndex  = 1;
    private int  totalJobs        = 1;
    private boolean totalJobsKnown = false;

    private String  completeMessage = null;
    private long    autoCloseAt     = 0;

    private AbstractButton pauseButton = null;

    private record EtaSample(long timestamp, int completed) {}
    private final ArrayDeque<EtaSample> etaSamples = new ArrayDeque<>();

    public PreGenScreen() {
        super(Component.translatable("genesis.pregen.title"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        int btnY = this.height - 44;
        int btnW = 84;
        int gap  = 6;
        int totalW = 3 * btnW + 2 * gap;
        int btnX = (this.width - totalW) / 2;

        addRenderableWidget(GenesisButton.of(btnX, btnY, btnW, 20, "genesis.pregen.cancel",
                () -> { GenesisNetwork.sendPreGenControl(GenesisNetwork.ControlAction.CANCEL); PreGenHud.reset(); onClose(); })
                .tip("genesis.pregen.cancel.tip"));

        pauseButton = addRenderableWidget(GenesisButton.of(
                btnX + btnW + gap, btnY, btnW, 20, paused ? "genesis.pregen.resume" : "genesis.pregen.pause",
                () -> GenesisNetwork.sendPreGenControl(GenesisNetwork.ControlAction.PAUSE_RESUME))
                .tip("genesis.pregen.pause.tip"));

        addRenderableWidget(GenesisButton.of(
                btnX + 2 * (btnW + gap), btnY, btnW, 20, "genesis.pregen.enter",
                () -> {
                    // Auto-resume so pregen keeps running in the background
                    if (paused) GenesisNetwork.sendPreGenControl(GenesisNetwork.ControlAction.PAUSE_RESUME);
                    GenesisNetwork.sendPreGenControl(GenesisNetwork.ControlAction.ENTER_WORLD);
                    onClose();
                })
                .tip("genesis.pregen.enter.tip"));
    }

    @Override
    public void tick() {
        if (autoCloseAt > 0 && System.currentTimeMillis() >= autoCloseAt) {
            onClose();
        }
    }

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
    }
    //?}

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Dark full-screen overlay
        gfx.fill(0, 0, this.width, this.height, GenesisTheme.SCRIM_72);

        if (completeMessage != null) {
            // Completion state
            int mw = this.font.width(completeMessage);
            gfx.drawString(this.font, completeMessage,
                    (this.width - mw) / 2, this.height / 2 - 6,
                    GenesisTheme.FORGED_GOLD, false);
        } else {
            // Title
            String title = I18n.get("genesis.pregen.title");
            gfx.drawString(this.font, title,
                    (this.width - this.font.width(title)) / 2, this.height / 2 - 68,
                    GenesisTheme.FORGED_GOLD, false);

            // Dimension line
            String dimLine = dimText + (totalJobs > 1 ? "  (" + currentJobIndex + " / " + totalJobs + ")" : "");
            gfx.drawString(this.font, dimLine,
                    (this.width - this.font.width(dimLine)) / 2, this.height / 2 - 52,
                    GenesisTheme.STEEL_GRAY, false);

            // Progress bar
            int barW = (int)(this.width * 0.6);
            int barX = (this.width - barW) / 2;
            int barY = this.height / 2 - 30;
            int barH = 12;
            int barBorder = GenesisTheme.HAIRLINE;
            int filled = totalChunks > 0 ? (int)((long) barW * completedChunks / totalChunks) : 0;
            gfx.fill(barX, barY, barX + barW, barY + barH, GenesisTheme.RAISED_FILL);
            if (filled > 0) {
                gfx.fill(barX, barY, barX + filled, barY + barH, GenesisTheme.FORGED_GOLD);
            }
            // Bar border
            GenesisTheme.drawBorder(gfx, barX, barY, barW, barH, barBorder);

            // Counter
            double pct = totalChunks > 0 ? (100.0 * completedChunks / totalChunks) : 0.0;
            String counter = String.format("%,d / %,d  (%.1f%%)", completedChunks, totalChunks, pct);
            gfx.drawString(this.font, counter,
                    (this.width - this.font.width(counter)) / 2, barY + barH + 6,
                    GenesisTheme.STEEL_GRAY, false);

            // ETA
            String eta = computeEta(completedChunks, totalChunks);
            gfx.drawString(this.font, eta,
                    (this.width - this.font.width(eta)) / 2, barY + barH + 18,
                    GenesisTheme.STEEL_GRAY, false);

            // Paused indicator
            if (paused) {
                String pausedMsg = I18n.get("genesis.pregen.paused");
                gfx.drawString(this.font, pausedMsg,
                        (this.width - this.font.width(pausedMsg)) / 2, barY + barH + 30,
                        GenesisTheme.FORGED_GOLD, false);
            }
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /** Called from the main thread by GenesisNetwork's S2C handler. */
    public void onProgressPacket(ResourceLocation dim, int completed, int total,
                                  int queueRemaining, boolean paused, boolean done) {
        this.dimText        = dim.toString();
        this.completedChunks = completed;
        this.totalChunks    = total;
        this.paused         = paused;

        if (!totalJobsKnown) {
            totalJobs     = 1 + queueRemaining;
            totalJobsKnown = true;
        }

        etaSamples.addLast(new EtaSample(System.currentTimeMillis(), completed));
        if (etaSamples.size() > 10) etaSamples.removeFirst();

        if (pauseButton != null) {
            pauseButton.setMessage(Component.translatable(paused ? "genesis.pregen.resume" : "genesis.pregen.pause"));
        }

        if (done && queueRemaining == 0) {
            completeMessage = I18n.get("genesis.pregen.complete");
            autoCloseAt     = System.currentTimeMillis() + 2000;
        } else if (done) {
            // Next dimension starting - reset progress state
            currentJobIndex++;
            completedChunks = 0;
            etaSamples.clear();
        }
    }

    private String computeEta(int completed, int total) {
        if (etaSamples.size() < 2) return I18n.get("genesis.pregen.calculating");
        EtaSample oldest = etaSamples.peekFirst();
        EtaSample newest = etaSamples.peekLast();
        if (oldest == null || newest == null) return I18n.get("genesis.pregen.calculating");
        long dtMs     = newest.timestamp() - oldest.timestamp();
        int  dtChunks = newest.completed()  - oldest.completed();
        if (dtMs <= 0 || dtChunks <= 0) return I18n.get("genesis.pregen.calculating");
        double chunksPerMs = (double) dtChunks / dtMs;
        long remainingMs   = (long)((total - completed) / chunksPerMs);
        long seconds = remainingMs / 1000;
        if (seconds < 60) return I18n.get("genesis.pregen.eta_seconds", seconds);
        return I18n.get("genesis.pregen.eta_minutes", seconds / 60);
    }
}
