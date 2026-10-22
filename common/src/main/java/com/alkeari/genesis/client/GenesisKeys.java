package com.alkeari.genesis.client;

import com.alkeari.genesis.client.screen.WorldMapScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Genesis key bindings; each loader registers them, so they appear and can be changed in the Controls screen. */
public final class GenesisKeys {

    public static final KeyMapping WORLD_MAP = new KeyMapping(
            "key.genesis.world_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.genesis");

    private GenesisKeys() {}

    /** Opens the world map when its key was pressed in game with no screen open. */
    static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (WORLD_MAP.consumeClick()) {
            if (mc.screen == null && mc.level != null && mc.player != null) mc.setScreen(new WorldMapScreen());
        }
    }
}
