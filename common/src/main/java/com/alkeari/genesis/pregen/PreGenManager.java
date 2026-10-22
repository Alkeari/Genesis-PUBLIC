package com.alkeari.genesis.pregen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PreGenManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/PreGenManager");
    private static final Queue<PreGenJob> jobQueue = new ConcurrentLinkedQueue<>();
    private static volatile PreGenJob currentJob = null;

    private PreGenManager() {}

    public static synchronized void start(MinecraftServer server, List<ServerLevel> levels,
                                           int centerBlockX, int centerBlockZ,
                                           int radius, int concurrency) {
        int cx = centerBlockX >> 4;
        int cz = centerBlockZ >> 4;
        LOGGER.info("[PreGenManager] Queuing {} job(s) - center=({},{}) radius={} concurrency={}",
                levels.size(), cx, cz, radius, concurrency);
        for (ServerLevel level : levels) {
            LOGGER.info("[PreGenManager] Queued dim={} center=({},{})", level.dimension().location(), cx, cz);
            jobQueue.add(new PreGenJob(server, level, cx, cz, radius, concurrency));
        }
        advanceQueue();
    }

    public static synchronized void cancel() {
        PreGenJob job = currentJob;
        if (job == null) {
            LOGGER.warn("[PreGenManager] cancel() called with no active job");
            return;
        }
        LOGGER.info("[PreGenManager] Cancelling dim={} completed={}/{}", job.dimension, job.completed.get(), job.total);
        job.cancelled.set(true);
        job.notifyResume();
        int cleared = jobQueue.size();
        jobQueue.clear();
        if (cleared > 0) LOGGER.info("[PreGenManager] Cleared {} queued job(s)", cleared);
    }

    public static synchronized void togglePause() {
        PreGenJob job = currentJob;
        if (job == null) return;
        boolean nowPaused = !job.paused.get();
        job.paused.set(nowPaused);
        if (!nowPaused) job.notifyResume();
        //noinspection LoggingSimilarMessage
        LOGGER.info("[PreGenManager] {} dim={}", nowPaused ? "Paused" : "Resumed", job.dimension);
        job.broadcastCurrent();
    }

    /** Returns false if no job is running or already paused. */
    public static synchronized boolean pause() {
        PreGenJob job = currentJob;
        if (job == null) {
            LOGGER.warn("[PreGenManager] pause() called with no active job");
            return false;
        }
        if (job.paused.get()) {
            LOGGER.warn("[PreGenManager] pause() called but dim={} already paused", job.dimension);
            return false;
        }
        job.paused.set(true);
        LOGGER.info("[PreGenManager] Paused dim={}", job.dimension);
        job.broadcastCurrent();
        return true;
    }

    /** Returns false if no job is running or not currently paused. */
    public static synchronized boolean resume() {
        PreGenJob job = currentJob;
        if (job == null) {
            LOGGER.warn("[PreGenManager] resume() called with no active job");
            return false;
        }
        if (!job.paused.get()) {
            LOGGER.warn("[PreGenManager] resume() called but dim={} not paused", job.dimension);
            return false;
        }
        job.paused.set(false);
        job.notifyResume();
        LOGGER.info("[PreGenManager] Resumed dim={}", job.dimension);
        job.broadcastCurrent();
        return true;
    }

    // A job ended by stopAll finishes after a later world may have started its own, which it must not touch.
    public static synchronized void onJobComplete(PreGenJob finished) {
        if (currentJob != finished) return;
        LOGGER.info("[PreGenManager] Job finished - dim={} queueRemaining={}", finished.dimension, jobQueue.size());
        currentJob = null;
        advanceQueue();
    }

    /** Ends every job with the server, so a later world on the same game starts with none. */
    public static synchronized void stopAll() {
        jobQueue.clear();
        PreGenJob job = currentJob;
        if (job != null) {
            job.cancelled.set(true);
            job.notifyResume();
        }
        currentJob = null;
    }

    public static synchronized boolean isRunning() {
        PreGenJob job = currentJob;
        return job != null && job.isAlive();
    }

    public static synchronized int getQueueRemaining() {
        return jobQueue.size();
    }

    public static PreGenStatus getStatus() {
        PreGenJob job = currentJob;
        if (job == null) return null;
        return new PreGenStatus(
                job.dimension,
                job.completed.get(),
                job.total,
                jobQueue.size(),
                job.paused.get(),
                job.isAlive());
    }

    private static void advanceQueue() {
        if (currentJob != null && currentJob.isAlive()) return;
        PreGenJob next = jobQueue.poll();
        if (next == null) {
            LOGGER.info("[PreGenManager] Queue empty - all jobs complete");
            return;
        }
        LOGGER.info("[PreGenManager] Starting next job - dim={} queueRemaining={}", next.dimension, jobQueue.size());
        currentJob = next;
        currentJob.start();
    }
}
