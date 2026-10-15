package com.example.sailing;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One race at a time. Marks are rounded in order; a sailor counts as "at a mark" when their boat
 * gets within 6 blocks of it. Start with the race clipboard.
 */
public final class Race {
    private Race() {}

    private static final class Racer {
        String name;
        int next;
        boolean finished;
        boolean dnf;
        long finishTicks;
    }

    private static final Map<UUID, List<BlockPos>> COURSES = new HashMap<>();
    private static final Map<UUID, Racer> RACERS = new LinkedHashMap<>();
    private static List<BlockPos> marks = new ArrayList<>();
    private static ServerWorld raceWorld;
    private static int state;      // 0 idle, 1 countdown, 2 running
    private static int countdown;
    private static long startTick;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Race::tick);
    }

    public static List<BlockPos> course(UUID player) {
        return COURSES.computeIfAbsent(player, k -> new ArrayList<>());
    }

    public static void startOrCancel(ServerWorld world, ServerPlayerEntity host) {
        if (state != 0) {
            broadcast(world.getServer(), host.getName().getString() + " cancelled the race.", false);
            reset();
            return;
        }
        List<BlockPos> course = course(host.getUuid());
        if (course.size() < 2) {
            host.sendMessage(Text.literal("Add at least 2 marks first (right-click buoys with the clipboard)."), true);
            return;
        }

        RACERS.clear();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(host) > 160 * 160) continue;
            if (RiggedBoats.rigOf(p.getVehicle()) == null) continue;
            Racer r = new Racer();
            r.name = p.getName().getString();
            RACERS.put(p.getUuid(), r);
        }
        if (RACERS.isEmpty()) {
            host.sendMessage(Text.literal("Nobody is sitting in a rigged dinghy nearby."), true);
            return;
        }

        marks = new ArrayList<>(course);
        raceWorld = world;
        state = 1;
        countdown = 200;
        broadcast(world.getServer(), "Race starting in 10 seconds: " + marks.size() + " marks, "
                + RACERS.size() + " sailor" + (RACERS.size() == 1 ? "" : "s") + ".", false);
    }

    private static void reset() {
        state = 0;
        RACERS.clear();
        raceWorld = null;
    }

    private static void broadcast(MinecraftServer server, String text, boolean actionBar) {
        for (UUID id : RACERS.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) p.sendMessage(Text.literal(text), actionBar);
        }
    }

    private static String fmt(long ticks) {
        long ms = ticks * 50;
        return String.format(Locale.ROOT, "%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }

    private static void tick(MinecraftServer server) {
        if (state == 0 || raceWorld == null) return;

        if (state == 1) {
            countdown--;
            int secs = (countdown + 19) / 20;
            if (countdown > 0 && countdown % 20 == 0 && (secs <= 5 || secs == 10)) {
                broadcast(server, "Start in " + secs + "...", true);
            }
            if (countdown <= 0) {
                state = 2;
                startTick = raceWorld.getTime();
                broadcast(server, "GO!", false);
            }
            return;
        }

        long now = raceWorld.getTime() - startTick;
        boolean allDone = true;

        for (Map.Entry<UUID, Racer> e : RACERS.entrySet()) {
            Racer r = e.getValue();
            if (r.finished || r.dnf) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                r.dnf = true;
                continue;
            }
            allDone = false;

            Entity boat = p.getVehicle();
            if (boat == null) continue;

            BlockPos m = marks.get(r.next);
            double dx = m.getX() + 0.5 - boat.getX();
            double dz = m.getZ() + 0.5 - boat.getZ();
            double dist = Math.hypot(dx, dz);

            if (dist <= 6) {
                r.next++;
                if (r.next >= marks.size()) {
                    r.finished = true;
                    r.finishTicks = now;
                    broadcast(server, r.name + " finished in " + fmt(now) + "!", false);
                    continue;
                }
                p.sendMessage(Text.literal("Mark " + r.next + "/" + marks.size() + " rounded. " + fmt(now)), false);
                continue;
            }

            if (now % 5 == 0) {
                double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                p.sendMessage(Text.literal(String.format(Locale.ROOT,
                        "Race %s | Mark %d/%d: bearing %03.0f, %.0f m",
                        fmt(now), r.next + 1, marks.size(), Wind.bearing(yaw), dist)), true);
            }
        }

        if (allDone || now > 20L * 60 * 20) finish(server);
    }

    private static void finish(MinecraftServer server) {
        List<Racer> done = new ArrayList<>();
        for (Racer r : RACERS.values()) if (r.finished) done.add(r);
        done.sort((a, b) -> Long.compare(a.finishTicks, b.finishTicks));

        StringBuilder sb = new StringBuilder("Race results:");
        if (done.isEmpty()) sb.append(" nobody finished.");
        for (int i = 0; i < done.size(); i++) {
            sb.append('\n').append(i + 1).append(". ").append(done.get(i).name)
                    .append("  ").append(fmt(done.get(i).finishTicks));
        }
        broadcast(server, sb.toString(), false);
        reset();
    }
}
