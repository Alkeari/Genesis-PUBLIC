package com.alkeari.genesis;

import com.alkeari.genesis.client.GenesisClient;
import com.alkeari.genesis.client.network.GenesisNetwork;
import com.alkeari.genesis.command.GenesisCommand;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.modtrix.api.Events;
import com.alkeari.modtrix.api.Modtrix;
import com.alkeari.modtrix.api.Updates;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Genesis {
    public static final String MOD_ID = "genesis";
    public static final Logger LOGGER = LoggerFactory.getLogger("genesis");
    public static final String UPDATE_URL = "https://alkeari.com/updates/genesis.json";
    private static final AtomicBoolean UPDATE_CHECK_DECIDED = new AtomicBoolean();

    /** Called by every loader's entrypoint during mod construction, after the config is registered. */
    public static void init() {
        GenesisNetwork.init();
        GenesisCommand.register();
        // A lambda, not GenesisClient::init: a method reference resolves GenesisClient on a dedicated server too, and its verification loads Screen.
        Modtrix.onClient(() -> GenesisClient.init());
        // Decided on the first tick, since Forge and NeoForge load the config only after mod construction.
        Runnable decide = () -> {
            if (UPDATE_CHECK_DECIDED.compareAndSet(false, true) && GenesisConfig.checkForUpdates) {
                Updates.check(MOD_ID, UPDATE_URL);
            }
        };
        Events.onClientTick(decide);
        Events.onServerTick(server -> decide.run());
        LOGGER.info("{} loaded.", MOD_ID);
    }
}
