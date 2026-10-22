package com.alkeari.genesis.client.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.alkeari.genesis.Genesis;
import com.alkeari.genesis.map.StructureLocator;
import com.alkeari.genesis.map.WorldMapService;
import com.alkeari.modtrix.api.MessageType;
import com.alkeari.modtrix.api.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class GenesisPackets {

    record PreGenStart(int radius, int concurrency, int centerBlockX, int centerBlockZ) {}

    record PreGenControl(byte action) {}

    record SetSpawn(int blockX, int blockZ) {}

    record MapAvailable(boolean available) {}

    record MapRequest(int minChunkX, int minChunkZ) {}

    record PreGenProgress(ResourceLocation dimension, int completed, int total,
                          int queueRemaining, boolean paused, boolean done) {}

    private static MessageType<PreGenStart> start;
    private static MessageType<PreGenControl> control;
    private static MessageType<SetSpawn> setSpawn;
    private static MessageType<MapAvailable> mapAvailable;
    private static MessageType<MapRequest> mapRequest;
    private static MessageType<WorldMapService.Region> mapRegion;
    private static MessageType<PreGenProgress> progress;

    private GenesisPackets() {}

    /** Registers every message type on both sides; handlers run on the game thread. */
    static void register() {
        start = Network.toServer(id("pregen_start"), PreGenStart.class,
                (p, buf) -> {
                    buf.writeInt(p.radius());
                    buf.writeInt(p.concurrency());
                    buf.writeInt(p.centerBlockX());
                    buf.writeInt(p.centerBlockZ());
                },
                buf -> new PreGenStart(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()),
                (p, player) -> GenesisNetwork.onPreGenStart(player, p.radius(), p.concurrency(),
                        p.centerBlockX(), p.centerBlockZ()));
        control = Network.toServer(id("pregen_control"), PreGenControl.class,
                (p, buf) -> buf.writeByte(p.action()),
                buf -> new PreGenControl(buf.readByte()),
                (p, player) -> GenesisNetwork.onPreGenControl(player, p.action()));
        setSpawn = Network.toServer(id("set_spawn"), SetSpawn.class,
                (p, buf) -> {
                    buf.writeInt(p.blockX());
                    buf.writeInt(p.blockZ());
                },
                buf -> new SetSpawn(buf.readInt(), buf.readInt()),
                (p, player) -> GenesisNetwork.onSetSpawn(player, p.blockX(), p.blockZ()));
        mapAvailable = Network.toClient(id("map_available"), MapAvailable.class,
                (p, buf) -> buf.writeBoolean(p.available()),
                buf -> new MapAvailable(buf.readBoolean()),
                p -> GenesisNetwork.onMapAvailable(p.available()));
        mapRequest = Network.toServer(id("map_request"), MapRequest.class,
                (p, buf) -> {
                    buf.writeInt(p.minChunkX());
                    buf.writeInt(p.minChunkZ());
                },
                buf -> new MapRequest(buf.readInt(), buf.readInt()),
                (p, player) -> WorldMapService.request(player, p.minChunkX(), p.minChunkZ(),
                        region -> mapRegion.sendTo(player, region)));
        mapRegion = Network.toClient(id("map_region"), WorldMapService.Region.class,
                (p, buf) -> {
                    buf.writeResourceLocation(p.dimension());
                    buf.writeInt(p.minChunkX());
                    buf.writeInt(p.minChunkZ());
                    buf.writeVarInt(p.palette().size());
                    for (ResourceLocation biome : p.palette()) buf.writeResourceLocation(biome);
                    for (short index : p.indices()) buf.writeShort(index);
                    buf.writeVarInt(p.structures().size());
                    for (StructureLocator.Marker marker : p.structures()) {
                        buf.writeInt(marker.chunkX());
                        buf.writeInt(marker.chunkZ());
                        buf.writeResourceLocation(marker.structure());
                    }
                },
                buf -> {
                    ResourceLocation dimension = buf.readResourceLocation();
                    int minChunkX = buf.readInt();
                    int minChunkZ = buf.readInt();
                    int size = buf.readVarInt();
                    List<ResourceLocation> palette = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) palette.add(buf.readResourceLocation());
                    short[] indices = new short[WorldMapService.REGION * WorldMapService.REGION];
                    for (int i = 0; i < indices.length; i++) indices[i] = buf.readShort();
                    int count = buf.readVarInt();
                    List<StructureLocator.Marker> structures = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        structures.add(new StructureLocator.Marker(buf.readInt(), buf.readInt(), buf.readResourceLocation()));
                    }
                    return new WorldMapService.Region(dimension, minChunkX, minChunkZ, palette, indices, structures);
                },
                GenesisNetwork::onMapRegion);
        progress = Network.toClient(id("pregen_progress"), PreGenProgress.class,
                (p, buf) -> {
                    buf.writeResourceLocation(p.dimension());
                    buf.writeInt(p.completed());
                    buf.writeInt(p.total());
                    buf.writeInt(p.queueRemaining());
                    buf.writeBoolean(p.paused());
                    buf.writeBoolean(p.done());
                },
                buf -> new PreGenProgress(buf.readResourceLocation(),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readBoolean(), buf.readBoolean()),
                p -> GenesisNetwork.onPreGenProgress(p.dimension(), p.completed(),
                        p.total(), p.queueRemaining(), p.paused(), p.done()));
    }

    static void sendPreGenStart(int radius, int concurrency, int centerBlockX, int centerBlockZ) {
        start.sendToServer(new PreGenStart(radius, concurrency, centerBlockX, centerBlockZ));
    }

    static void sendPreGenControl(byte action) {
        control.sendToServer(new PreGenControl(action));
    }

    static void sendSetSpawn(int blockX, int blockZ) {
        setSpawn.sendToServer(new SetSpawn(blockX, blockZ));
    }

    static void sendMapAvailable(ServerPlayer player, boolean available) {
        mapAvailable.sendTo(player, new MapAvailable(available));
    }

    static void sendMapRequest(int minChunkX, int minChunkZ) {
        mapRequest.sendToServer(new MapRequest(minChunkX, minChunkZ));
    }

    static void sendPreGenProgress(List<ServerPlayer> players, ResourceLocation dimension,
                                   int completed, int total, int queueRemaining, boolean paused, boolean done) {
        PreGenProgress message = new PreGenProgress(dimension, completed, total, queueRemaining, paused, done);
        for (ServerPlayer player : players) {
            progress.sendTo(player, message);
        }
    }

    private static ResourceLocation id(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild(Genesis.MOD_ID, path));
    }
}
