package com.example.sailing.client;

import com.example.sailing.BoatClass;
import com.example.sailing.SailMath;
import com.example.sailing.SailStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A boat you are steering is simulated by YOUR client, so the sailing forces have to be applied
 * here. The server sends the wind, sheet setting and class every tick; each client tick we let
 * vanilla do its thing, then replace the boat's velocity with sail-driven motion.
 *
 * Only does anything while you're riding a rigged boat and fresh data is arriving.
 */
public class SailingClient implements ClientModInitializer {
    private static volatile SailStatePayload state;
    private static volatile long stateNanos;

    private static boolean tracking;
    private static float yawBefore;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SailStatePayload.ID, (payload, context) -> {
            state = payload;
            stateNanos = System.nanoTime();
        });
        ClientTickEvents.START_CLIENT_TICK.register(SailingClient::start);
        ClientTickEvents.END_CLIENT_TICK.register(SailingClient::end);
    }

    private static AbstractBoatEntity ridden(MinecraftClient mc) {
        if (mc.player == null) return null;
        Entity v = mc.player.getVehicle();
        return v instanceof AbstractBoatEntity b ? b : null;
    }

    private static boolean fresh(SailStatePayload s, AbstractBoatEntity b) {
        return s != null && s.boatId() == b.getId() && System.nanoTime() - stateNanos < 400_000_000L;
    }

    private static void start(MinecraftClient mc) {
        AbstractBoatEntity b = ridden(mc);
        tracking = b != null && fresh(state, b);
        if (tracking) yawBefore = b.getYaw();
    }

    private static void end(MinecraftClient mc) {
        if (!tracking || mc.world == null) return;
        AbstractBoatEntity b = ridden(mc);
        SailStatePayload s = state;
        if (b == null || s == null || !fresh(s, b)) return;

        BoatClass cls = BoatClass.of(s.flags() & 15);
        boolean capsized = (s.flags() & 16) != 0;

        Vec3d v = b.getVelocity();
        double h = b.getYaw();
        double rad = Math.toRadians(h);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double cur = v.x * fx + v.z * fz;

        // The rudder only bites when the boat is moving: scale what vanilla steering just did.
        double dy = MathHelper.wrapDegrees(h - yawBefore);
        double authority = MathHelper.clamp(0.25 + Math.abs(cur) / 0.15, 0.25, 1.0);
        h = yawBefore + dy * authority;
        b.setYaw((float) h);
        rad = Math.toRadians(h);
        fx = -Math.sin(rad);
        fz = Math.cos(rad);

        // How the wind meets the sail.
        double wind = s.windFrom();
        double rel = MathHelper.wrapDegrees(wind - h);
        double ar = Math.abs(rel);
        double knots = Math.min(s.knots(), 28);
        double opt = SailMath.optTrim(ar);
        double eff = MathHelper.clamp(1 - Math.abs(s.trim() - opt) / 40.0, 0.12, 1.0);

        double target = capsized ? 0 : cls.vmax * (knots / 20.0) * SailMath.polar(ar) * eff;
        target = Math.min(target, cls.vmax * 1.25);

        // Heading into the swell costs speed.
        double upwind = Math.max(0, Math.cos(Math.toRadians(rel)));
        target *= 1 - 0.5 * s.waveAmp() * upwind;

        double newSpeed = cur + (target - cur) * (target > cur ? 0.04 : 0.07);

        // Keel: most sideways motion is killed. Wind then pushes you sideways (leeway).
        double lx = (v.x - fx * cur) * 0.35;
        double lz = (v.z - fz * cur) * 0.35;
        double wx = Math.sin(Math.toRadians(wind)), wz = -Math.cos(Math.toRadians(wind));
        double along = wx * fx + wz * fz;
        double px = wx - fx * along, pz = wz - fz * along;
        double lee = 0.0022 * knots * (1.2 - eff * 0.6);
        lx += px * lee;
        lz += pz * lee;

        // Waves: bob the boat up and down with the swell.
        double t = mc.world.getTime();
        double kick = SailMath.waveHeight(b.getX(), b.getZ(), t + 1, wind, s.waveAmp())
                - SailMath.waveHeight(b.getX(), b.getZ(), t, wind, s.waveAmp());

        b.setVelocity(new Vec3d(fx * newSpeed + lx, v.y + kick * 0.9, fz * newSpeed + lz));
    }
}
