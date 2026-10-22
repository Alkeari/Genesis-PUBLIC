package com.alkeari.genesis.forge;

import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.client.screen.PreGenHud;
import com.alkeari.genesis.client.screen.PreGenScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
//? if >=1.20.6 {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.event.TickEvent;
//?} else {
/*import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
*///?}
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = Genesis.MOD_ID, value = Dist.CLIENT)
public class GenesisForgeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisForgeClient");

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("[GenesisForgeClient] World join - resetting PreGenHud");
        PreGenHud.reset();
    }

    //? if >=1.20.6 {
    // Forge 50 and 51 have no event for a HUD layer, so the HUD is drawn after the frame, and only with no screen open,
    // since a screen would otherwise be drawn under it.
    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!PreGenHud.isActive() || mc.screen != null || mc.level == null || mc.options.hideGui) return;
        GuiGraphics gfx = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        PreGenHud.render(gfx, mc.font,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        gfx.flush();
    }
    //?} else {
    /*@SuppressWarnings("unused")
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!PreGenHud.isActive() || mc.screen instanceof PreGenScreen) return;
        PreGenHud.render(event.getGuiGraphics(), mc.font,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
    }
    *///?}
}
