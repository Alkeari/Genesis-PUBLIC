package com.alkeari.genesis.forge;

import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.client.GenesisKeys;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Key registration fires on the mod bus, unlike the game events in {@link GenesisForgeClient}. */
@Mod.EventBusSubscriber(modid = Genesis.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GenesisForgeKeys {

    private GenesisForgeKeys() {}

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(GenesisKeys.WORLD_MAP);
    }
}
