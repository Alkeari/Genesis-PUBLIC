package com.alkeari.genesis.pregen;

import com.alkeari.genesis.client.network.GenesisNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
//? if >=1.20.5 {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?} else
/*import net.minecraft.world.level.chunk.ChunkStatus;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PreGenJob extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/PreGenJob");

    private final MinecraftServer server;
    private final ServerLevel     level;
    private final int             centerChunkX;
    private final int             centerChunkZ;
    private final int             radiusChunks;
    private final int             concurrency;

    final AtomicBoolean cancelled    = new AtomicBoolean(false);
    final AtomicBoolean paused       = new AtomicBoolean(false);
    private final Object pauseMonitor = new Object();
    final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private volatile boolean finished = false;
    final int           total;
    final ResourceLocation dimension;

    @SuppressWarnings("unused")
    PreGenJob(MinecraftServer server, ServerLevel level,
              int centerChunkX, int centerChunkZ,
              int radiusChunks, int concurrency) {
        super("genesis-pregen-" + level.dimension().location().getPath());
        this.server       = server;
        this.level        = level;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radiusChunks = radiusChunks;
        this.concurrency  = concurrency;
        this.total        = (2 * radiusChunks + 1) * (2 * radiusChunks + 1);
        this.dimension    = level.dimension().location();
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY + 1);
    }

    @Override
    public void run() {
        LOGGER.info("[PreGen] Starting - dim={} center=({},{}) radius={} total={} concurrency={}",
                dimension, centerChunkX, centerChunkZ, radiusChunks, total, concurrency);

        broadcastCurrent();
        List<ChunkPos> spiral = buildSpiral(radiusChunks, centerChunkX, centerChunkZ);
        Semaphore slots = new Semaphore(concurrency);
        long startNs = System.nanoTime();

        try {
            spiral:
            for (ChunkPos pos : spiral) {
                if (cancelled.get()) break;
                if (!server.isRunning()) {
                    LOGGER.warn("[PreGen] Server stopped mid-run - dim={} completed={}/{}",
                            dimension, completed.get(), total);
                    break;
                }
                waitWhilePaused();
                if (cancelled.get()) break;
                // Rechecked while waiting: a stopping server never runs the tasks that would free a slot.
                while (!slots.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                    if (cancelled.get() || !server.isRunning()) break spiral;
                }
                server.execute(() ->
                    level.getChunkSource()
                         .getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
                         .whenComplete((result, error) -> {
                             if (error == null && generated(result)) completed.incrementAndGet();
                             else failed.incrementAndGet();
                             slots.release();
                             maybeBroadcastProgress();
                         })
                );
            }
            if (!cancelled.get() && server.isRunning()) {
                LOGGER.info("[PreGen] Draining {} in-flight chunk futures - dim={}", concurrency, dimension);
                while (!slots.tryAcquire(concurrency, 250, TimeUnit.MILLISECONDS)) {
                    if (cancelled.get() || !server.isRunning()) break;
                }
            }
        } catch (InterruptedException e) {
            LOGGER.warn("[PreGen] Thread interrupted - dim={} completed={}/{}", dimension, completed.get(), total);
            Thread.currentThread().interrupt();
        }

        long elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000L;
        if (failed.get() > 0) {
            LOGGER.warn("[PreGen] {} chunk(s) failed to generate and are not counted - dim={}", failed.get(), dimension);
        }
        if (cancelled.get()) {
            LOGGER.info("[PreGen] Cancelled - dim={} completed={}/{} elapsed={}s",
                    dimension, completed.get(), total, elapsedSec);
        } else {
            LOGGER.info("[PreGen] Complete - dim={} completed={}/{} elapsed={}s",
                    dimension, completed.get(), total, elapsedSec);
        }

        finished = true;
        broadcastFinal();
        PreGenManager.onJobComplete(this);
    }

    private static boolean generated(Object result) {
        //? if >=1.20.5 {
        return result instanceof net.minecraft.server.level.ChunkResult<?> chunk && chunk.isSuccess();
        //?} else
        /*return result instanceof com.mojang.datafixers.util.Either<?, ?> chunk && chunk.left().isPresent();*/
    }

    void notifyResume() {
        synchronized (pauseMonitor) {
            pauseMonitor.notifyAll();
        }
    }

    private void waitWhilePaused() throws InterruptedException {
        if (!paused.get() || cancelled.get()) return;
        LOGGER.info("[PreGen] Paused - dim={} completed={}/{}", dimension, completed.get(), total);
        synchronized (pauseMonitor) {
            while (paused.get() && !cancelled.get()) {
                pauseMonitor.wait(100);
            }
        }
        if (!cancelled.get()) {
            LOGGER.info("[PreGen] Resumed - dim={} completed={}/{}", dimension, completed.get(), total);
        }
    }

    private void maybeBroadcastProgress() {
        // Chunks still in flight after a cancel finish later and must not bring the HUD back.
        if (finished) return;
        int done = completed.get();
        if (done % 64 == 0 || total < 64) broadcastCurrent();
        if (total > 0 && done > 0) {
            int pctNow  = (int)(100L * done / total);
            int pctPrev = (int)(100L * (done - 1) / total);
            if (pctNow != pctPrev && pctNow % 25 == 0) {
                LOGGER.info("[PreGen] {}% - dim={} {}/{} chunks", pctNow, dimension, done, total);
            }
        }
    }

    void broadcastCurrent() {
        GenesisNetwork.broadcastProgress(server, dimension,
                completed.get(), total,
                PreGenManager.getQueueRemaining(),
                paused.get(), false);
    }

    private void broadcastFinal() {
        GenesisNetwork.broadcastProgress(server, dimension,
                completed.get(), total,
                PreGenManager.getQueueRemaining(),
                false, true);
        if (PreGenManager.getQueueRemaining() == 0) {
            server.execute(() -> GenesisNetwork.restoreAllBlockedPlayers(server));
        }
    }

    private static List<ChunkPos> buildSpiral(int radius, int cx, int cz) {
        List<ChunkPos> list = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int r = 0; r <= radius; r++) {
            if (r == 0) { list.add(new ChunkPos(cx, cz)); continue; }
            for (int dx = -r; dx <= r; dx++) {
                list.add(new ChunkPos(cx + dx, cz - r));
                list.add(new ChunkPos(cx + dx, cz + r));
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                list.add(new ChunkPos(cx - r, cz + dz));
                list.add(new ChunkPos(cx + r, cz + dz));
            }
        }
        return list;
    }
}
