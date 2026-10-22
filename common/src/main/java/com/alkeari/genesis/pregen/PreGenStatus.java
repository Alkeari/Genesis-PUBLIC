package com.alkeari.genesis.pregen;

import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("unused")
public record PreGenStatus(
        ResourceLocation dimension,
        int completed,
        int total,
        int queueRemaining,
        boolean paused,
        boolean running
) {}
