package com.alkeari.genesis.neoforge;

import com.alkeari.genesis.client.GenesisKeys;
import com.alkeari.genesis.client.screen.PreGenHud;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.alkeari.genesis.client.screen.PreGenScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
//? if >=1.20.5 {
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
//?} else {
/*import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only listeners; registered by {@link GenesisNeoForge} in the game client only. */
public final class GenesisNeoForgeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisNeoForgeClient");

    private GenesisNeoForgeClient() {}

    static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(GenesisKeys.WORLD_MAP));
        NeoForge.EVENT_BUS.addListener(GenesisNeoForgeClient::onPlayerLoggingIn);
        NeoForge.EVENT_BUS.addListener(GenesisNeoForgeClient::onRenderHud);
    }

    private static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("[GenesisNeoForgeClient] World join - resetting PreGenHud");
        PreGenHud.reset();
    }

    //? if >=1.20.5 {
    private static void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
    //?} else {
    /*private static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
    *///?}
        Minecraft mc = Minecraft.getInstance();
        if (!PreGenHud.isActive() || mc.screen instanceof PreGenScreen) return;
        PreGenHud.render(event.getGuiGraphics(), mc.font,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
    }
}
