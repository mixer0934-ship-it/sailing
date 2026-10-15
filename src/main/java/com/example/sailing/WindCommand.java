package com.example.sailing;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

/**
 * /wind              shows the current wind
 * /wind set <from> <knots>   forces a wind (handy for testing). NOTE: not restricted to ops.
 * /wind auto         back to the natural wind
 */
public final class WindCommand {
    private WindCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("wind")
                        .executes(ctx -> {
                            double from = Wind.fromYaw(ctx.getSource().getServer());
                            double kn = Wind.knots(ctx.getSource().getServer());
                            String text = String.format("Wind: %.0f knots from the %s (%.0f degrees), waves up to %.2f blocks%s",
                                    kn, Wind.compass(from), Wind.bearing(from), Wind.waveAmp(kn) * 1.5,
                                    Wind.isOverridden() ? " [forced]" : "");
                            ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                            return 1;
                        })
                        .then(CommandManager.literal("auto").executes(ctx -> {
                            Wind.clearOverride();
                            ctx.getSource().sendFeedback(() -> Text.literal("Wind is natural again."), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("from", IntegerArgumentType.integer(0, 359))
                                        .then(CommandManager.argument("knots", IntegerArgumentType.integer(0, 40))
                                                .executes(ctx -> {
                                                    int bearing = IntegerArgumentType.getInteger(ctx, "from");
                                                    int knots = IntegerArgumentType.getInteger(ctx, "knots");
                                                    // compass bearing -> Minecraft yaw
                                                    double yaw = ((bearing - 180) % 360 + 360) % 360;
                                                    Wind.setOverride(yaw, knots);
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "Wind forced: " + knots + " knots from " + bearing + " degrees."), true);
                                                    return 1;
                                                }))))));
    }
}
