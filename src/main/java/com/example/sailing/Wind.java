package com.example.sailing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/**
 * One wind for the whole world. It's a pure function of world time, so every player sees the
 * same wind and nothing needs saving. Rain and thunder make it stronger.
 *
 * Directions are Minecraft yaws: "from" is the yaw you face to look straight into the wind.
 */
public final class Wind {
    private Wind() {}

    private static volatile double overrideFrom = Double.NaN;
    private static volatile double overrideKnots = Double.NaN;

    public static void setOverride(double fromYaw, double knots) {
        overrideFrom = fromYaw;
        overrideKnots = knots;
    }

    public static void clearOverride() {
        overrideFrom = Double.NaN;
        overrideKnots = Double.NaN;
    }

    public static boolean isOverridden() {
        return !Double.isNaN(overrideFrom);
    }

    public static double fromYaw(MinecraftServer server) {
        if (!Double.isNaN(overrideFrom)) return overrideFrom;
        ServerWorld w = server.getOverworld();
        double t = w.getTime();
        long seed = w.getSeed();
        double base = Math.floorMod(seed, 360L);
        double from = base + 55 * Math.sin(t / 9000.0 + (seed % 7))
                + 18 * Math.sin(t / 1700.0) + 6 * Math.sin(t / 330.0);
        return ((from % 360) + 360) % 360;
    }

    public static double knots(MinecraftServer server) {
        if (!Double.isNaN(overrideKnots)) return overrideKnots;
        ServerWorld w = server.getOverworld();
        double t = w.getTime();
        long seed = w.getSeed();
        double kn = 10 + 6 * Math.sin(t / 4300.0 + (seed % 5))
                + 3.5 * Math.sin(t / 900.0 + 1.1) + 2.0 * Math.sin(t / 160.0 + 2.3);
        if (w.isRaining()) kn += 3;
        if (w.isThundering()) kn += 6;
        return Math.max(1, Math.min(32, kn));
    }

    /** Wave height in blocks for a wind speed. Flat calm below ~4 knots. */
    public static double waveAmp(double knots) {
        return Math.max(0, Math.min(1, (knots - 4) / 22.0)) * 0.45;
    }

    /** Compass bearing (0 = north, clockwise) of a Minecraft yaw. */
    public static double bearing(double yaw) {
        return ((yaw + 180) % 360 + 360) % 360;
    }

    private static final String[] POINTS = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    /** Name of the compass point the wind blows FROM, e.g. "NW". */
    public static String compass(double fromYaw) {
        int i = (int) Math.round(bearing(fromYaw) / 22.5) & 15;
        return POINTS[i];
    }
}
