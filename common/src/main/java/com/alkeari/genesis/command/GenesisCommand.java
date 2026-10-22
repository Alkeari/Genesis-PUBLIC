package com.alkeari.genesis.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.alkeari.genesis.config.GenesisConfig;
import com.alkeari.genesis.map.AccuracyCheck;
import com.alkeari.modtrix.api.Events;
import com.alkeari.genesis.pregen.PreGenManager;
import com.alkeari.genesis.pregen.PreGenStatus;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class GenesisCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("genesis/GenesisCommand");

    private GenesisCommand() {}

    public static void register() {
        Events.onRegisterCommands(GenesisCommand::onRegister);
        if (AccuracyCheck.ENABLED) Events.onServerTick(AccuracyCheck::onServerTick);
    }

    // Allows single-player regardless of the "Allow Commands" (cheats) world option.
    // On dedicated/multiplayer servers, OP level 2 is still required.
    private static boolean hasGenesisPermission(CommandSourceStack src) {
        return src.getServer().isSingleplayer() || src.hasPermission(2);
    }

    @SuppressWarnings("unused")
    private static void onRegister(CommandDispatcher<CommandSourceStack> dispatcher,
                                    CommandBuildContext buildContext,
                                    Commands.CommandSelection selection) {
        LOGGER.info("[GenesisCommand] Registering /genesis commands (selection={})", selection);
        if (AccuracyCheck.ENABLED) {
            dispatcher.register(Commands.literal("genesis")
                .then(Commands.literal("verify")
                    .requires(GenesisCommand::hasGenesisPermission)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
                        .executes(ctx -> verify(ctx, 1))
                        .then(Commands.argument("step", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> verify(ctx, IntegerArgumentType.getInteger(ctx, "step")))))));
        }
        dispatcher.register(
            Commands.literal("genesis")
                .then(Commands.literal("pregen")
                    .then(Commands.literal("all")
                        .requires(GenesisCommand::hasGenesisPermission)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 2048))
                            .executes(ctx -> startAll(ctx,
                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                    GenesisConfig.pregenDefaultThreads))
                            .then(Commands.argument("threads", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> startAll(ctx,
                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "threads"))))))
                    .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .requires(GenesisCommand::hasGenesisPermission)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 2048))
                            .executes(ctx -> startDim(ctx,
                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                    GenesisConfig.pregenDefaultThreads))
                            .then(Commands.argument("threads", IntegerArgumentType.integer(1, 16))
                                .executes(ctx -> startDim(ctx,
                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                        IntegerArgumentType.getInteger(ctx, "threads"))))))
                    .then(Commands.literal("at")
                        .requires(GenesisCommand::hasGenesisPermission)
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .then(Commands.literal("all")
                                    .requires(GenesisCommand::hasGenesisPermission)
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 2048))
                                        .executes(ctx -> startAllAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                GenesisConfig.pregenDefaultThreads,
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z")))
                                        .then(Commands.argument("threads", IntegerArgumentType.integer(1, 16))
                                            .executes(ctx -> startAllAt(ctx,
                                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                                    IntegerArgumentType.getInteger(ctx, "threads"),
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z"))))))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                    .requires(GenesisCommand::hasGenesisPermission)
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 2048))
                                        .executes(ctx -> startDimAt(ctx,
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                GenesisConfig.pregenDefaultThreads,
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z")))
                                        .then(Commands.argument("threads", IntegerArgumentType.integer(1, 16))
                                            .executes(ctx -> startDimAt(ctx,
                                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                                    IntegerArgumentType.getInteger(ctx, "threads"),
                                                    IntegerArgumentType.getInteger(ctx, "x"),
                                                    IntegerArgumentType.getInteger(ctx, "z")))))))))
                    .then(Commands.literal("cancel")
                        .requires(GenesisCommand::hasGenesisPermission)
                        .executes(GenesisCommand::cancelPreGen))
                    .then(Commands.literal("pause")
                        .requires(GenesisCommand::hasGenesisPermission)
                        .executes(GenesisCommand::pausePreGen))
                    .then(Commands.literal("resume")
                        .requires(GenesisCommand::hasGenesisPermission)
                        .executes(GenesisCommand::resumePreGen))
                    .then(Commands.literal("status")
                        .executes(GenesisCommand::statusPreGen)))
                .then(Commands.literal("info")
                    .executes(GenesisCommand::info))
        );
    }

    private static int startAll(CommandContext<CommandSourceStack> ctx,
                                 int radius, int threads) {
        if (PreGenManager.isRunning()) {
            LOGGER.warn("[GenesisCommand] /genesis pregen all rejected - job already running src={}",
                    ctx.getSource().getTextName());
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] A pre-generation job is already running. Use /genesis pregen cancel first."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        Vec3 pos = ctx.getSource().getPosition();
        int cx = Mth.floor(pos.x);
        int cz = Mth.floor(pos.z);
        LOGGER.info("[GenesisCommand] /genesis pregen all - center=({},{}) radius={} threads={} dims={} src={}",
                cx, cz, radius, threads, levels.size(), ctx.getSource().getTextName());
        PreGenManager.start(server, levels, cx, cz, radius, threads);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Queued pre-generation for " + levels.size() +
            " dimensions (radius " + radius + ")."), true);
        return 1;
    }

    private static int startDim(CommandContext<CommandSourceStack> ctx,
                                  int radius, int threads) throws CommandSyntaxException {
        if (PreGenManager.isRunning()) {
            LOGGER.warn("[GenesisCommand] /genesis pregen <dim> rejected - job already running src={}",
                    ctx.getSource().getTextName());
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] A pre-generation job is already running. Use /genesis pregen cancel first."));
            return 0;
        }
        ServerLevel level  = DimensionArgument.getDimension(ctx, "dimension");
        MinecraftServer srv = ctx.getSource().getServer();
        Vec3 pos = ctx.getSource().getPosition();
        int cx = Mth.floor(pos.x);
        int cz = Mth.floor(pos.z);
        long total = (2L * radius + 1) * (2L * radius + 1);
        String dimId = level.dimension().location().toString();
        LOGGER.info("[GenesisCommand] /genesis pregen {} - center=({},{}) radius={} threads={} total={} src={}",
                dimId, cx, cz, radius, threads, total, ctx.getSource().getTextName());
        PreGenManager.start(srv, List.of(level), cx, cz, radius, threads);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Pre-generation started - " + total + " chunks in " + dimId +
            " (radius " + radius + ", " + threads + " threads)"), true);
        return 1;
    }

    private static int startAllAt(CommandContext<CommandSourceStack> ctx,
                                    int radius, int threads, int cx, int cz) {
        if (PreGenManager.isRunning()) {
            LOGGER.warn("[GenesisCommand] /genesis pregen at {} {} all rejected - job already running src={}",
                    cx, cz, ctx.getSource().getTextName());
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] A pre-generation job is already running. Use /genesis pregen cancel first."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        LOGGER.info("[GenesisCommand] /genesis pregen at {} {} all - radius={} threads={} dims={} src={}",
                cx, cz, radius, threads, levels.size(), ctx.getSource().getTextName());
        PreGenManager.start(server, levels, cx, cz, radius, threads);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Queued pre-generation for " + levels.size() +
            " dimensions (radius " + radius + ", center " + cx + ", " + cz + ")."), true);
        return 1;
    }

    private static int startDimAt(CommandContext<CommandSourceStack> ctx,
                                   int radius, int threads, int cx, int cz) throws CommandSyntaxException {
        if (PreGenManager.isRunning()) {
            LOGGER.warn("[GenesisCommand] /genesis pregen at {} {} <dim> rejected - job already running src={}",
                    cx, cz, ctx.getSource().getTextName());
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] A pre-generation job is already running. Use /genesis pregen cancel first."));
            return 0;
        }
        ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
        MinecraftServer srv = ctx.getSource().getServer();
        long total = (2L * radius + 1) * (2L * radius + 1);
        String dimId = level.dimension().location().toString();
        LOGGER.info("[GenesisCommand] /genesis pregen at {} {} {} - radius={} threads={} total={} src={}",
                cx, cz, dimId, radius, threads, total, ctx.getSource().getTextName());
        PreGenManager.start(srv, List.of(level), cx, cz, radius, threads);
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Pre-generation started - " + total + " chunks in " + dimId +
            " (radius " + radius + ", center " + cx + ", " + cz + ", " + threads + " threads)"), true);
        return 1;
    }

    private static int cancelPreGen(CommandContext<CommandSourceStack> ctx) {
        if (!PreGenManager.isRunning()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] No pre-generation job is currently running."));
            return 0;
        }
        PreGenStatus status = PreGenManager.getStatus();
        int done = status != null ? status.completed() : 0;
        LOGGER.info("[GenesisCommand] /genesis pregen cancel - done={} src={}",
                done, ctx.getSource().getTextName());
        PreGenManager.cancel();
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Pre-generation cancelled. " + done + " chunks were generated."), true);
        return 1;
    }

    private static int pausePreGen(CommandContext<CommandSourceStack> ctx) {
        if (!PreGenManager.isRunning()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] No pre-generation job is currently running."));
            return 0;
        }
        if (!PreGenManager.pause()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] Pre-generation is already paused."));
            return 0;
        }
        LOGGER.info("[GenesisCommand] /genesis pregen pause - src={}", ctx.getSource().getTextName());
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Pre-generation paused."), true);
        return 1;
    }

    private static int resumePreGen(CommandContext<CommandSourceStack> ctx) {
        if (!PreGenManager.isRunning()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] No pre-generation job is currently running."));
            return 0;
        }
        if (!PreGenManager.resume()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] Pre-generation is not paused."));
            return 0;
        }
        LOGGER.info("[GenesisCommand] /genesis pregen resume - src={}", ctx.getSource().getTextName());
        ctx.getSource().sendSuccess(() -> Component.literal(
            "[Genesis] Pre-generation resumed."), true);
        return 1;
    }

    private static int statusPreGen(CommandContext<CommandSourceStack> ctx) {
        PreGenStatus status = PreGenManager.getStatus();
        if (status == null || !status.running()) {
            ctx.getSource().sendFailure(Component.literal(
                "[Genesis] No pre-generation job is currently running."));
            return 0;
        }
        String pct = String.format("%.1f%%",
                status.total() > 0 ? (100.0 * status.completed() / status.total()) : 0.0);
        String msg = "[Genesis] Pre-gen: " + status.dimension() +
                     " - " + status.completed() + " / " + status.total() + " (" + pct + ")" +
                     (status.queueRemaining() > 0
                         ? " - " + status.queueRemaining() + " dimension(s) queued" : "");
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int verify(CommandContext<CommandSourceStack> ctx, int step) {
        Vec3 pos = ctx.getSource().getPosition();
        boolean started = AccuracyCheck.start(ctx.getSource(), ctx.getSource().getLevel(),
                Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4, IntegerArgumentType.getInteger(ctx, "radius"), step);
        if (!started) {
            ctx.getSource().sendFailure(Component.literal("[Genesis] An accuracy check is already running."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Genesis] Accuracy check started."), false);
        return 1;
    }

    @SuppressWarnings("SameReturnValue")
    private static int info(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerLevel level = ctx.getSource().getLevel();
            long seed  = level.getSeed();
            String dim = level.dimension().location().toString();
            String gen = level.getChunkSource().getGenerator().getClass().getSimpleName();
            ctx.getSource().sendSuccess(() -> Component.literal("[Genesis] Seed: " + seed), false);
            ctx.getSource().sendSuccess(() -> Component.literal("[Genesis] Dimension: " + dim), false);
            ctx.getSource().sendSuccess(() -> Component.literal("[Genesis] Generator: " + gen), false);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[Genesis] Failed to retrieve world info."));
        }
        return 1;
    }
}
