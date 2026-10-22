package com.alkeari.genesis.fabric;

import com.alkeari.genesis.client.GenesisKeys;
import com.alkeari.genesis.client.screen.PreGenHud;
import com.alkeari.genesis.client.screen.PreGenScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
@SuppressWarnings("unused")
public class GenesisFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisFabricClient");

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(GenesisKeys.WORLD_MAP);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("[GenesisFabricClient] World join - resetting PreGenHud");
            PreGenHud.reset();
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!PreGenHud.isActive() || mc.screen instanceof PreGenScreen) return;
            PreGenHud.render(guiGraphics, mc.font,
                    mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight());
        });
    }
}
