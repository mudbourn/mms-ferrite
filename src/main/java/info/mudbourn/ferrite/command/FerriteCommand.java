package info.mudbourn.ferrite.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import info.mudbourn.ferrite.RustBridge;
import info.mudbourn.ferrite.config.FerriteConfig;
import info.mudbourn.ferrite.entity.CrammingDispatcher;
import info.mudbourn.ferrite.monitor.EntityQueryMonitor;
import info.mudbourn.ferrite.monitor.HopperHintMonitor;
import info.mudbourn.ferrite.monitor.MonitorLog;
import info.mudbourn.ferrite.spatial.ColliderSkip;
import info.mudbourn.ferrite.spatial.EntityCellIndex;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class FerriteCommand {

    private FerriteCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("ferrite")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cramming")
                    .then(Commands.literal("on").executes(ctx -> cramming(ctx, true)))
                    .then(Commands.literal("off").executes(ctx -> cramming(ctx, false)))
                    .then(Commands.literal("status").executes(FerriteCommand::crammingStatus)))
                .then(Commands.literal("hopper")
                    .then(Commands.literal("hint")
                        .then(Commands.literal("on").executes(ctx -> hopperHint(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> hopperHint(ctx, false)))
                        .then(Commands.literal("validate")
                            .then(Commands.literal("on").executes(ctx -> hopperValidate(ctx, true)))
                            .then(Commands.literal("off").executes(ctx -> hopperValidate(ctx, false))))
                        .then(Commands.literal("status").executes(FerriteCommand::hopperStatus))))
                .then(Commands.literal("entityquery")
                    .then(Commands.literal("status").executes(FerriteCommand::entityQueryStatus)))
                .then(Commands.literal("log")
                    .then(Commands.literal("monitors")
                        .then(Commands.literal("on").executes(ctx -> logMonitors(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> logMonitors(ctx, false)))
                        .then(Commands.literal("status").executes(FerriteCommand::logStatus))))));
    }

    private static int cramming(CommandContext<CommandSourceStack> ctx, boolean on) {
        CrammingDispatcher.ENABLED = on && RustBridge.NATIVE_AVAILABLE;
        FerriteConfig.set(FerriteConfig.KEY_CRAMMING, on, true);
        return reply(ctx, "[cramming] " + (on ? "batched native path on" : "vanilla path on"), true);
    }

    private static int crammingStatus(CommandContext<CommandSourceStack> ctx) {
        return reply(
            ctx,
            String.format(
                "[cramming] enabled=%s native=%s",
                CrammingDispatcher.ENABLED,
                RustBridge.NATIVE_AVAILABLE ? "available" : "missing"
            ),
            false
        );
    }

    private static int hopperHint(CommandContext<CommandSourceStack> ctx, boolean on) {
        HopperHintMonitor.USE_HINT = on;
        FerriteConfig.set(FerriteConfig.KEY_HOPPER_HINT, on, true);
        return reply(ctx, "[hopper-hint] " + (on ? "on" : "off"), true);
    }

    private static int hopperValidate(CommandContext<CommandSourceStack> ctx, boolean on) {
        HopperHintMonitor.VALIDATE = on;
        return reply(ctx, "[hopper-hint] validator " + (on ? "on" : "off"), true);
    }

    private static int hopperStatus(CommandContext<CommandSourceStack> ctx) {
        return reply(
            ctx,
            String.format("[hopper-hint] useHint=%s validate=%s", HopperHintMonitor.USE_HINT, HopperHintMonitor.VALIDATE),
            false
        );
    }

    private static int entityQueryStatus(CommandContext<CommandSourceStack> ctx) {
        return reply(
            ctx,
            String.format(
                "[entity-query] cache=%s colliderSkip=%s oracle 1/%d, mismatches=%d, collider nonEmpty=%d",
                EntityCellIndex.ENABLED,
                ColliderSkip.ENABLED,
                EntityCellIndex.ORACLE_RATE,
                EntityQueryMonitor.totalMismatches(),
                EntityQueryMonitor.totalColliderNonEmpty()
            ),
            false
        );
    }

    private static int logMonitors(CommandContext<CommandSourceStack> ctx, boolean on) {
        MonitorLog.ENABLED = on;
        FerriteConfig.set(FerriteConfig.KEY_LOG_MONITORS, on, !MonitorLog.SMALL_HEAP);
        return reply(ctx, "[log] monitor reports " + (on ? "on" : "off"), true);
    }

    private static int logStatus(CommandContext<CommandSourceStack> ctx) {
        return reply(ctx, "[log] monitors=" + (MonitorLog.ENABLED ? "on" : "off"), false);
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, String message, boolean broadcast) {
        ctx.getSource().sendSuccess(() -> Component.literal(message), broadcast);
        return Command.SINGLE_SUCCESS;
    }
}
