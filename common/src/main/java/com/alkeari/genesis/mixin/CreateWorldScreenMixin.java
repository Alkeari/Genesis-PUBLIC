package com.alkeari.genesis.mixin;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.client.screen.GenesisOverlayWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {

    @Unique private static final Logger GENESIS_LOGGER = LoggerFactory.getLogger("genesis/CreateWorldScreenMixin");

    // Extending Screen rather than shadowing addRenderableWidget: a shadow of an inherited
    // method gets no refmap entry, so Forge's SRG runtime cannot find it.
    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    // Drives isMouseOver in the click-zone widget and collapsed/expanded drawing
    @Unique private boolean genesis_expanded = false;

    // -------------------------------------------------------------------------
    // Layout constants
    // -------------------------------------------------------------------------

    @Unique private static final String PANEL_LABEL    = "✦  GENESIS";
    @Unique private static final int    PANEL_H        = 20;
    @Unique private static final int    ICON_ONLY_W    = 20;
    @Unique private static final int    GENESIS_PADDING = 10;

    @Unique
    private int genesis_expandedWidth() {
        return Minecraft.getInstance().font.width(PANEL_LABEL) + GENESIS_PADDING * 2;
    }

    // -------------------------------------------------------------------------
    // Widget injection - adds an invisible click zone in the top-right corner.
    // Using init() rather than mouseClicked() avoids remapping issues on Fabric:
    // mouseClicked is not directly defined in CreateWorldScreen so Loom cannot
    // remap it, causing silent injection failure in Fabric production builds.
    // The widget participates in the normal widget dispatch which is remapped.
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    @Inject(method = "init", at = @At("RETURN"))
    private void genesis_addOverlayWidget(CallbackInfo ci) {
        try {
            //noinspection DataFlowIssue
            CreateWorldScreen self = (CreateWorldScreen)(Object) this;
            int expandedW = genesis_expandedWidth();
            addRenderableWidget(new GenesisOverlayWidget(
                    self.width - expandedW, 0, expandedW, PANEL_H, self
            ));
        } catch (Throwable t) {
            GENESIS_LOGGER.error("[Genesis] genesis_addOverlayWidget FAILED - widget will not be clickable", t);
        }
    }

    // -------------------------------------------------------------------------
    // Render injection - draws the visual overlay on top of every other widget
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unused", "NameDoesntMatchTargetClass"})
    @Inject(method = "render", at = @At("TAIL"))
    private void genesis_renderOverlay(GuiGraphics gfx, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo ci) {
        //noinspection DataFlowIssue
        CreateWorldScreen self = (CreateWorldScreen)(Object) this;
        genesis_drawPanel(gfx, self.width, mouseX, mouseY);
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    @Unique
    private void genesis_drawPanel(GuiGraphics gfx, int screenW, int mouseX, int mouseY) {
        int expandedW = genesis_expandedWidth();

        // Sticky hover: stays expanded while cursor is anywhere in current panel
        int hitLeft = genesis_expanded ? (screenW - expandedW) : (screenW - ICON_ONLY_W);
        genesis_expanded = mouseX >= hitLeft && mouseX <= screenW
                        && mouseY >= 0        && mouseY < PANEL_H;

        int panelLeft = genesis_expanded ? (screenW - expandedW) : (screenW - ICON_ONLY_W);

        // Background
        gfx.fill(panelLeft, 0, screenW, PANEL_H, GenesisTheme.DEEP_GRAPHITE);

        // Gold bottom border - visually anchors the panel to the screen top edge
        gfx.fill(panelLeft, PANEL_H - 1, screenW, PANEL_H, GenesisTheme.FORGED_GOLD);

        // Gold left border - marks the panel boundary
        gfx.fill(panelLeft, 0, panelLeft + 1, PANEL_H, GenesisTheme.FORGED_GOLD);

        int textY = (PANEL_H - 8) / 2; // vertically centre 8px MC font in 20px panel

        if (genesis_expanded) {
            gfx.drawString(Minecraft.getInstance().font, PANEL_LABEL,
                    panelLeft + GENESIS_PADDING, textY, GenesisTheme.FORGED_GOLD, false);
        } else {
            String icon  = "✦";
            int    iconW = Minecraft.getInstance().font.width(icon);
            gfx.drawString(Minecraft.getInstance().font, icon,
                    panelLeft + (ICON_ONLY_W - iconW) / 2, textY,
                    GenesisTheme.FORGED_GOLD, false);
        }
    }
}
