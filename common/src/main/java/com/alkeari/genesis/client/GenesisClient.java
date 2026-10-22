package com.alkeari.genesis.client;

import com.alkeari.genesis.client.network.GenesisNetwork;
import com.alkeari.genesis.client.screen.PreGenScreen;
import com.alkeari.genesis.pregen.PendingPreGen;
import com.alkeari.genesis.pregen.PendingSpawn;
import com.alkeari.modtrix.api.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only setup shared by every loader; never loaded on a dedicated server. */
public final class GenesisClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisClient");

    private GenesisClient() {}

    public static void init() {
        Events.onClientTick(GenesisClient::applyPendingWorldSetup);
        Events.onClientTick(GenesisKeys::onClientTick);
    }

    /**
     * Once the new world is loaded and no screen is open, sends the spawn point chosen on the preview map,
     * then opens the progress screen and asks the server to pre-generate around that spawn.
     */
    private static void applyPendingWorldSetup() {
        Minecraft mc = Minecraft.getInstance();
        if (!PendingSpawn.hasPending() && !PendingPreGen.hasPending()) return;
        if (mc.level == null || mc.screen != null || mc.player == null) return;
        BlockPos playerPos = mc.player.blockPosition();
        int centerX = playerPos.getX();
        int centerZ = playerPos.getZ();
        if (PendingSpawn.hasPending()) {
            centerX = PendingSpawn.getBlockX();
            centerZ = PendingSpawn.getBlockZ();
            LOGGER.info("[GenesisClient] PendingSpawn detected - spawn=({},{})", centerX, centerZ);
            PendingSpawn.clear();
            GenesisNetwork.sendSetSpawn(centerX, centerZ);
        }
        if (!PendingPreGen.hasPending()) return;
        int radius      = PendingPreGen.getRadius();
        int concurrency = PendingPreGen.getConcurrency();
        LOGGER.info("[GenesisClient] PendingPreGen detected - radius={} concurrency={}", radius, concurrency);
        PendingPreGen.clear();
        mc.setScreen(new PreGenScreen());
        GenesisNetwork.sendPreGenStart(radius, concurrency, centerX, centerZ);
    }
}
