package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.mixin.CreateWorldScreenAccessor;
import com.alkeari.genesis.mixin.WorldCreationUiStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invisible click zone for the Genesis overlay panel.
 * <p>
 * The visual (collapsed icon / expanded label) is drawn entirely by
 * CreateWorldScreenMixin.genesis_drawPanel() on the render thread.
 * This widget exists solely to receive click events through Minecraft's
 * normal widget dispatch.
 * <p>
 * getX() is overridden to compute dynamically from the current window
 * width so the click zone stays correct across window resizes, even if
 * init() has not been recalled with the updated dimensions yet.
 * <p>
 * onPress() re-reads the current WorldCreationContext from the parent
 * at press time, so changes to world type (Superflat, Large Biomes, etc.)
 * are always reflected.
 */
public final class GenesisOverlayWidget extends AbstractButton {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisOverlayWidget");

    private final CreateWorldScreen parent;

    public GenesisOverlayWidget(int x, int y, int fullWidth, int height,
                                 CreateWorldScreen parent) {
        super(x, y, fullWidth, height, Component.empty());
        this.parent = parent;
        setTooltip(Tooltip.create(Component.translatable("genesis.overlay.tooltip")));
    }

    /**
     * Always position the click zone at the right edge of the current window.
     * This keeps the zone aligned with the visual panel even after a window
     * resize that happened while a different screen (e.g. GenesisScreen) was
     * shown, before CreateWorldScreen.init() has been called with the new size.
     */
    @Override
    public int getX() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth() - this.width;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.active || !this.visible) return false;
        return mouseX >= this.getX()
            && mouseX <= this.getX() + this.getWidth()
            && mouseY >= this.getY()
            && mouseY <  this.getY() + this.getHeight();
    }

    @Override
    public void onPress() {
        WorldCreationContext ctx = currentContext();
        LOGGER.info("[Genesis] onPress - opening GenesisScreen");
        try {
            Minecraft.getInstance().setScreen(new GenesisScreen(parent, ctx));
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to open GenesisScreen", t);
        }
    }

    /** Fetches the live WorldCreationContext so world-type changes are reflected. */
    private WorldCreationContext currentContext() {
        try {
            WorldCreationUiState uiState =
                    ((CreateWorldScreenAccessor) parent).genesis_getUiState();
            return ((WorldCreationUiStateAccessor) uiState).genesis_getContext();
        } catch (Throwable t) {
            LOGGER.error("[Genesis] Failed to read current WorldCreationContext", t);
            return null;
        }
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Visual is handled entirely by CreateWorldScreenMixin.genesis_renderOverlay
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    @NotNull
    public Component getMessage() {
        return Component.translatable("genesis.overlay.label");
    }
}
