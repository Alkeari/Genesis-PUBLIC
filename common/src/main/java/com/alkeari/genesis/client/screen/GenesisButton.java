package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public final class GenesisButton extends AbstractButton {

    public enum Style { DEFAULT, PRIMARY }

    private static final int BG_NORMAL   = GenesisTheme.RAISED_FILL;
    private static final int BG_HOVER    = GenesisTheme.RAISED_HOVER;
    private static final int BORDER_DARK = GenesisTheme.HAIRLINE;

    private final Runnable action;
    private final Style style;
    @Nullable private final BooleanSupplier activeState;

    private GenesisButton(int x, int y, int w, int h, String label,
                          Style style, Runnable action,
                          @Nullable BooleanSupplier activeState) {
        super(x, y, w, h, Component.translatable(label));
        this.style       = style;
        this.action      = action;
        this.activeState = activeState;
    }

    public static GenesisButton of(int x, int y, int w, int h,
                                   String label, Runnable action) {
        return new GenesisButton(x, y, w, h, label, Style.DEFAULT, action, null);
    }

    public static GenesisButton primary(int x, int y, int w, int h,
                                        String label, Runnable action) {
        return new GenesisButton(x, y, w, h, label, Style.PRIMARY, action, null);
    }

    public static GenesisButton toggle(int x, int y, int w, int h,
                                       String label, Runnable action,
                                       BooleanSupplier activeState) {
        return new GenesisButton(x, y, w, h, label, Style.DEFAULT, action, activeState);
    }


    /** One-sentence tooltip, by translation key, so every interactive element explains itself on hover. */
    public GenesisButton tip(String text) {
        setTooltip(Tooltip.create(Component.translatable(text)));
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    // Progress from the resting look (0) to the lit look (1), eased over the design system's 160 ms.
    private float lit;
    private long lastFrame;

    private float litProgress(boolean target) {
        long now = System.nanoTime();
        float step = lastFrame == 0 ? 1f : (now - lastFrame) / (float) GenesisTheme.TRANSITION_NANOS;
        lastFrame = now;
        lit = target ? Math.min(1f, lit + step) : Math.max(0f, lit - step);
        return 1f - (1f - lit) * (1f - lit);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        boolean active = activeState != null && activeState.getAsBoolean();
        float t = litProgress(isHovered() || active);
        Minecraft mc = Minecraft.getInstance();

        int textColor;
        if (style == Style.PRIMARY) {
            gfx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    GenesisTheme.lerpColor(GenesisTheme.FORGED_GOLD, GenesisTheme.WARM_GOLD_SHADOW, t));
            textColor = GenesisTheme.OBSIDIAN_BLACK;
        } else {
            gfx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), GenesisTheme.lerpColor(BG_NORMAL, BG_HOVER, t));
            GenesisTheme.drawBorder(gfx, getX(), getY(), getWidth(), getHeight(),
                    GenesisTheme.lerpColor(BORDER_DARK, GenesisTheme.FORGED_GOLD, t));
            textColor = GenesisTheme.lerpColor(GenesisTheme.STEEL_GRAY, GenesisTheme.SOFT_CHAMPAGNE, t);
        }
        int textX = getX() + (getWidth()  - mc.font.width(getMessage())) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        gfx.drawString(mc.font, getMessage(), textX, textY, textColor, false);
        if (isFocused() && mc.getLastInputType().isKeyboard()) {
            GenesisTheme.drawFocusRing(gfx, getX(), getY(), getWidth(), getHeight());
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
