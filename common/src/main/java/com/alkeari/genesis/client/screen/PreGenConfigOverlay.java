package com.alkeari.genesis.client.screen;

import com.alkeari.genesis.client.GenesisTheme;
import com.alkeari.genesis.config.GenesisConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.components.Tooltip;

public final class PreGenConfigOverlay {

    @FunctionalInterface
    public interface StartCallback { void start(int radius, int concurrency); }

    private static final int OW = 160;
    private static final int OH = 120;

    // Layout inside the overlay, on the design system's 4 px grid
    private static final int FIELD_X         = 8;
    private static final int FIELD_H         = 20;
    private static final int RADIUS_LABEL_Y  = 24;
    private static final int RADIUS_BOX_Y    = 36;
    private static final int THREADS_LABEL_Y = 60;
    private static final int THREADS_BOX_Y   = 72;
    private static final int START_Y         = OH - 24;

    private final int ox;
    private final int oy;
    private final Runnable      onDismiss;
    private final StartCallback onStart;

    private final EditBox radiusField;
    private final EditBox threadsField;
    private EditBox focusedField;

    public PreGenConfigOverlay(Font font, int screenW, int screenH,
                                Runnable onDismiss, StartCallback onStart) {
        this.ox        = (screenW - OW) / 2;
        this.oy        = (screenH - OH) / 2;
        this.onDismiss = onDismiss;
        this.onStart   = onStart;

        radiusField = new EditBox(font, ox + FIELD_X + 4, oy + RADIUS_BOX_Y + 2, OW - FIELD_X * 2 - 8, 16, Component.empty());
        radiusField.setTooltip(Tooltip.create(Component.translatable("genesis.pregen.radius.tip")));
        radiusField.setMaxLength(8);
        radiusField.setValue(String.valueOf(GenesisConfig.pregenDefaultRadius));
        radiusField.setTextColor(GenesisTheme.SOFT_CHAMPAGNE);
        radiusField.setBordered(false);

        threadsField = new EditBox(font, ox + FIELD_X + 4, oy + THREADS_BOX_Y + 2, OW - FIELD_X * 2 - 8, 16, Component.empty());
        threadsField.setTooltip(Tooltip.create(Component.translatable("genesis.pregen.threads.tip")));
        threadsField.setMaxLength(4);
        threadsField.setValue(String.valueOf(GenesisConfig.pregenDefaultThreads));
        threadsField.setTextColor(GenesisTheme.SOFT_CHAMPAGNE);
        threadsField.setBordered(false);

        setFocusedField(radiusField);
    }

    private void setFocusedField(EditBox field) {
        if (focusedField != null) focusedField.setFocused(false);
        focusedField = field;
        field.setFocused(true);
    }

    public void tick() {
        //? if <1.20.2 {
        /*radiusField.tick();
        threadsField.tick();
        *///?}
    }

    public void render(GuiGraphics gfx, Font font, int mouseX, int mouseY) {
        // Background with gold border
        gfx.fill(ox, oy, ox + OW, oy + OH, GenesisTheme.DEEP_GRAPHITE);
        GenesisTheme.drawBorder(gfx, ox, oy, OW, OH, GenesisTheme.FORGED_GOLD);

        // Title bar
        gfx.fill(ox + 1, oy + 1, ox + OW - 1, oy + 16, GenesisTheme.RAISED_FILL);
        gfx.drawString(font, I18n.get("genesis.screen.pregen"), ox + 8, oy + 4, GenesisTheme.FORGED_GOLD, false);

        // ✕ dismiss button
        boolean overDismiss = overDismiss(mouseX, mouseY);
        gfx.drawString(font, "✕", ox + OW - 14, oy + 4,
                overDismiss ? GenesisTheme.FORGED_GOLD : GenesisTheme.STEEL_GRAY, false);

        boolean keyboard = Minecraft.getInstance().getLastInputType().isKeyboard();

        // RADIUS field
        gfx.drawString(font, I18n.get("genesis.pregen.radius"), ox + FIELD_X, oy + RADIUS_LABEL_Y, GenesisTheme.STEEL_GRAY, false);
        boolean radiusValid = isRadiusValid();
        int radiusBorder = radiusValid ? GenesisTheme.HAIRLINE : GenesisTheme.ALERT_AMBER;
        drawFieldBox(gfx, ox + FIELD_X, oy + RADIUS_BOX_Y, OW - FIELD_X * 2, FIELD_H, radiusBorder);
        if (keyboard && focusedField == radiusField) {
            GenesisTheme.drawFocusRing(gfx, ox + FIELD_X, oy + RADIUS_BOX_Y, OW - FIELD_X * 2, FIELD_H);
        }
        radiusField.render(gfx, mouseX, mouseY, 0);

        // THREADS field
        gfx.drawString(font, I18n.get("genesis.pregen.threads"), ox + FIELD_X, oy + THREADS_LABEL_Y, GenesisTheme.STEEL_GRAY, false);
        boolean threadsValid = isThreadsValid();
        int threadsBorder = threadsValid ? GenesisTheme.HAIRLINE : GenesisTheme.ALERT_AMBER;
        drawFieldBox(gfx, ox + FIELD_X, oy + THREADS_BOX_Y, OW - FIELD_X * 2, FIELD_H, threadsBorder);
        if (keyboard && focusedField == threadsField) {
            GenesisTheme.drawFocusRing(gfx, ox + FIELD_X, oy + THREADS_BOX_Y, OW - FIELD_X * 2, FIELD_H);
        }
        threadsField.render(gfx, mouseX, mouseY, 0);

        // START button
        boolean canStart = radiusValid && threadsValid;
        int btnBg    = canStart ? GenesisTheme.DEEP_GRAPHITE : GenesisTheme.RAISED_FILL;
        int btnLabel = canStart ? GenesisTheme.FORGED_GOLD   : GenesisTheme.DISABLED_TEXT;
        int btnY = oy + START_Y;
        gfx.fill(ox + FIELD_X, btnY, ox + OW - FIELD_X, btnY + FIELD_H, btnBg);
        gfx.fill(ox + FIELD_X, btnY, ox + OW - FIELD_X, btnY + 1, canStart ? GenesisTheme.FORGED_GOLD : GenesisTheme.HAIRLINE);
        gfx.fill(ox + FIELD_X, btnY + FIELD_H - 1, ox + OW - FIELD_X, btnY + FIELD_H, canStart ? GenesisTheme.FORGED_GOLD : GenesisTheme.HAIRLINE);
        String startLabel = I18n.get("genesis.pregen.start");
        int labelW = font.width(startLabel);
        gfx.drawString(font, startLabel, ox + (OW - labelW) / 2, btnY + 6, btnLabel, false);

        // The Start and dismiss controls are drawn rather than widgets, so their tooltips are drawn here too.
        if (overStart(mouseX, mouseY)) {
            gfx.renderTooltip(font, Component.translatable("genesis.pregen.start.tip"), mouseX, mouseY);
        } else if (overDismiss) {
            gfx.renderTooltip(font, Component.translatable("genesis.pregen.dismiss.tip"), mouseX, mouseY);
        }
    }

    private boolean overDismiss(double mouseX, double mouseY) {
        return mouseX >= ox + OW - 16 && mouseX < ox + OW - 4 && mouseY >= oy + 2 && mouseY < oy + 14;
    }

    private boolean overStart(double mouseX, double mouseY) {
        return mouseX >= ox + FIELD_X && mouseX < ox + OW - FIELD_X && mouseY >= oy + START_Y && mouseY < oy + START_Y + FIELD_H;
    }

    @SuppressWarnings("SameParameterValue")
    private void drawFieldBox(GuiGraphics gfx, int x, int y, int w, int h, int border) {
        gfx.fill(x, y, x + w, y + h, GenesisTheme.RAISED_FILL);
        GenesisTheme.drawBorder(gfx, x, y, w, h, border);
    }

    private boolean isRadiusValid() {
        try {
            int v = Integer.parseInt(radiusField.getValue().trim());
            return v >= 1 && v <= 2048;
        } catch (NumberFormatException e) { return false; }
    }

    private boolean isThreadsValid() {
        try {
            int v = Integer.parseInt(threadsField.getValue().trim());
            return v >= 1 && v <= 16;
        } catch (NumberFormatException e) { return false; }
    }

    @SuppressWarnings("SameReturnValue")
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ✕ dismiss
        if (overDismiss(mouseX, mouseY)) {
            onDismiss.run();
            return true;
        }
        // START button
        if (overStart(mouseX, mouseY) && isRadiusValid() && isThreadsValid()) {
            onStart.start(
                Integer.parseInt(radiusField.getValue().trim()),
                Integer.parseInt(threadsField.getValue().trim()));
            return true;
        }
        // Field click - check hit area explicitly to set focus reliably
        if (mouseX >= ox + FIELD_X && mouseX < ox + OW - FIELD_X) {
            if (mouseY >= oy + RADIUS_BOX_Y && mouseY < oy + RADIUS_BOX_Y + FIELD_H) {
                setFocusedField(radiusField);
                radiusField.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            if (mouseY >= oy + THREADS_BOX_Y && mouseY < oy + THREADS_BOX_Y + FIELD_H) {
                setFocusedField(threadsField);
                threadsField.mouseClicked(mouseX, mouseY, button);
                return true;
            }
        }
        return true; // consume all clicks when overlay is open
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onDismiss.run(); return true; }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            setFocusedField(focusedField == radiusField ? threadsField : radiusField);
            return true;
        }
        if (focusedField != null) return focusedField.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (focusedField != null) return focusedField.charTyped(chr, modifiers);
        return false;
    }
}
