package com.alkeari.genesis.client.network;

import com.alkeari.genesis.pregen.PreGenManager;
import com.alkeari.modtrix.api.Events;
import com.alkeari.genesis.client.screen.PreGenHud;
import com.alkeari.genesis.client.screen.PreGenScreen;
import com.alkeari.genesis.client.screen.WorldMapScreen;
import com.alkeari.genesis.map.WorldMapService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GenesisNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisNetwork");

    public enum ControlAction { CANCEL, PAUSE_RESUME, ENTER_WORLD }

    private static final Map<UUID, GameType> blockedPlayers = new ConcurrentHashMap<>();

    // The mode a player had before pre-generation is also kept as an entity tag, which is saved with the
    // player, so quitting or crashing mid-run cannot leave a survival or hardcore world stuck in spectator.
    private static final String PREVIOUS_MODE_TAG = "genesis.pregen_previous_mode.";

    private GenesisNetwork() {}

    /** Registers every packet type. Call from every platform during common init, on both sides. */
    public static void init() {
        LOGGER.info("[GenesisNetwork] Registering packet handlers");
        GenesisPackets.register();
        Events.onServerStopping(GenesisNetwork::restoreAllBlockedPlayers);
        Events.onServerStopping(server -> PreGenManager.stopAll());
        Events.onPlayerJoin(GenesisNetwork::restoreFromTag);
        Events.onPlayerJoin(player -> GenesisPackets.sendMapAvailable(player, WorldMapService.allowed()));
    }

    static void onPreGenStart(ServerPlayer player, int radius, int concurrency, int centerBlockX, int centerBlockZ) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!server.isSingleplayer() && !player.hasPermissions(2)) {
            LOGGER.warn("[GenesisNetwork] PREGEN_START rejected - player={} lacks OP permission",
                    player.getName().getString());
            return;
        }
        if (PreGenManager.isRunning()) {
            LOGGER.warn("[GenesisNetwork] PREGEN_START rejected - job already running, player={}",
                    player.getName().getString());
            return;
        }
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            LOGGER.warn("[GenesisNetwork] PREGEN_START rejected - overworld level not available");
            return;
        }
        LOGGER.info("[GenesisNetwork] PREGEN_START - player={} center=({},{}) radius={} concurrency={}",
                player.getName().getString(), centerBlockX, centerBlockZ, radius, concurrency);
        setSpectator(player);
        PreGenManager.start(server, List.of(overworld),
                centerBlockX, centerBlockZ,
                Math.max(1, Math.min(2048, radius)),
                Math.max(1, Math.min(16,   concurrency)));
    }

    static void onSetSpawn(ServerPlayer player, int blockX, int blockZ) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!server.isSingleplayer() && !player.hasPermissions(2)) {
            LOGGER.warn("[GenesisNetwork] SET_SPAWN rejected - player={} lacks OP permission",
                    player.getName().getString());
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            LOGGER.warn("[GenesisNetwork] SET_SPAWN rejected - overworld level not available");
            return;
        }
        // Loading the chunk generates it, so the heightmap below is the real surface.
        overworld.getChunk(blockX >> 4, blockZ >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        overworld.setDefaultSpawnPos(new BlockPos(blockX, y, blockZ), 0f);
        if (player.level() == overworld) {
            player.teleportTo(overworld, blockX + 0.5, y, blockZ + 0.5, player.getYRot(), player.getXRot());
        }
        LOGGER.info("[GenesisNetwork] SET_SPAWN - player={} spawn=({},{},{})",
                player.getName().getString(), blockX, y, blockZ);
    }

    static void onPreGenControl(ServerPlayer player, byte actionOrd) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!server.isSingleplayer() && !player.hasPermissions(2)) {
            LOGGER.warn("[GenesisNetwork] PREGEN_CONTROL rejected - player={} lacks OP permission",
                    player.getName().getString());
            return;
        }
        ControlAction[] actions = ControlAction.values();
        if (actionOrd < 0 || actionOrd >= actions.length) {
            LOGGER.warn("[GenesisNetwork] PREGEN_CONTROL rejected - invalid action ordinal={}", actionOrd);
            return;
        }
        ControlAction action = actions[actionOrd];
        LOGGER.info("[GenesisNetwork] PREGEN_CONTROL action={} player={}", action, player.getName().getString());
        switch (action) {
            case CANCEL       -> PreGenManager.cancel();
            case PAUSE_RESUME -> PreGenManager.togglePause();
            case ENTER_WORLD  -> restoreBlockedPlayer(player.getUUID(), server);
        }
    }

    static void onPreGenProgress(ResourceLocation dim, int completed, int total, int queueRemaining, boolean paused, boolean done) {
        if (done && queueRemaining == 0) {
            LOGGER.info("[GenesisNetwork] PREGEN_PROGRESS final - dim={} completed={}/{}", dim, completed, total);
        }
        Minecraft mc = Minecraft.getInstance();
        PreGenHud.update(dim, completed, total, queueRemaining, paused, done);
        if (mc.screen instanceof PreGenScreen screen) {
            screen.onProgressPacket(dim, completed, total, queueRemaining, paused, done);
        }
    }

    // -------------------------------------------------------------------------
    // Spectator-mode helpers (server-side only)
    // -------------------------------------------------------------------------

    public static void setSpectator(ServerPlayer player) {
        GameType prev = player.gameMode.getGameModeForPlayer();
        LOGGER.info("[GenesisNetwork] Switching player={} to SPECTATOR (was {})",
                player.getName().getString(), prev);
        blockedPlayers.put(player.getUUID(), prev);
        clearPreviousModeTag(player);
        player.addTag(PREVIOUS_MODE_TAG + prev.getName());
        player.setGameMode(GameType.SPECTATOR);
    }

    private static void restoreFromTag(ServerPlayer player) {
        if (blockedPlayers.containsKey(player.getUUID())) return;
        for (String tag : List.copyOf(player.getTags())) {
            if (!tag.startsWith(PREVIOUS_MODE_TAG)) continue;
            GameType gt = GameType.byName(tag.substring(PREVIOUS_MODE_TAG.length()), GameType.SURVIVAL);
            LOGGER.info("[GenesisNetwork] Restoring player={} to {} after an unfinished pre-generation",
                    player.getName().getString(), gt);
            player.setGameMode(gt);
        }
        clearPreviousModeTag(player);
    }

    private static void clearPreviousModeTag(ServerPlayer player) {
        for (String tag : List.copyOf(player.getTags())) {
            if (tag.startsWith(PREVIOUS_MODE_TAG)) player.removeTag(tag);
        }
    }

    public static void restoreBlockedPlayer(UUID uuid, MinecraftServer server) {
        GameType gt = blockedPlayers.remove(uuid);
        if (gt == null) {
            LOGGER.warn("[GenesisNetwork] restoreBlockedPlayer - no saved gamemode for uuid={}", uuid);
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) {
            //noinspection LoggingSimilarMessage
            LOGGER.info("[GenesisNetwork] Restoring player={} to {}", p.getName().getString(), gt);
            p.setGameMode(gt);
            clearPreviousModeTag(p);
        } else {
            LOGGER.warn("[GenesisNetwork] restoreBlockedPlayer - player offline uuid={} savedMode={}", uuid, gt);
        }
    }

    public static void restoreAllBlockedPlayers(MinecraftServer server) {
        if (!blockedPlayers.isEmpty()) {
            LOGGER.info("[GenesisNetwork] Restoring {} blocked player(s)", blockedPlayers.size());
        }
        blockedPlayers.forEach((uuid, gt) -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                LOGGER.info("[GenesisNetwork] Restoring player={} to {}", p.getName().getString(), gt);
                p.setGameMode(gt);
                clearPreviousModeTag(p);
            } else {
                LOGGER.warn("[GenesisNetwork] restoreAllBlockedPlayers - player offline uuid={} savedMode={}", uuid, gt);
            }
        });
        blockedPlayers.clear();
    }

    // -------------------------------------------------------------------------
    // Client -> Server helpers
    // -------------------------------------------------------------------------

    public static void sendPreGenStart(int radius, int concurrency, int centerBlockX, int centerBlockZ) {
        GenesisPackets.sendPreGenStart(radius, concurrency, centerBlockX, centerBlockZ);
    }

    public static void sendPreGenControl(ControlAction action) {
        GenesisPackets.sendPreGenControl((byte) action.ordinal());
    }

    public static void sendSetSpawn(int blockX, int blockZ) {
        GenesisPackets.sendSetSpawn(blockX, blockZ);
    }

    public static void sendMapRequest(int minChunkX, int minChunkZ) {
        GenesisPackets.sendMapRequest(minChunkX, minChunkZ);
    }

    static void onMapAvailable(boolean available) {
        WorldMapScreen.onServerMapAvailable(available);
    }

    static void onMapRegion(WorldMapService.Region region) {
        if (Minecraft.getInstance().screen instanceof WorldMapScreen screen) screen.onRegion(region);
    }

    // -------------------------------------------------------------------------
    // Server -> Client helpers
    // -------------------------------------------------------------------------

    public static void broadcastProgress(MinecraftServer server, ResourceLocation dimension,
                                          int completed, int total, int queueRemaining,
                                          boolean paused, boolean done) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        GenesisPackets.sendPreGenProgress(players, dimension, completed, total, queueRemaining, paused, done);
    }
}
