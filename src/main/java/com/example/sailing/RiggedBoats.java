package com.example.sailing;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Everything server-side about rigged dinghies: rigging, one-person limit, sail visuals
 * (two vanilla display entities), stress and capsizing, and sending the sailing state to the rider.
 * The actual sailing forces are applied by the rider's client (see SailingClient).
 */
public final class RiggedBoats {
    private RiggedBoats() {}

    public static final String TAG_RIG = "sl_rig";

    public static final class Rig {
        public final ServerWorld world;
        public final AbstractBoatEntity boat;
        public BoatClass cls = BoatClass.LASER;
        public String sail = "white";
        /** Sail sheeting, degrees the boom is allowed out from the centreline. */
        public double trim = 40;

        double stress;
        int capsized;
        int side = 1;
        double lastX, lastZ;
        boolean hasLast;
        double speedKn;
        UUID mast, sailVis;
        String visSail = "";
        int visFailures;

        Rig(ServerWorld world, AbstractBoatEntity boat) {
            this.world = world;
            this.boat = boat;
        }
    }

    private static final Map<UUID, Rig> RIGS = new HashMap<>();

    private static final String MAST_TRANSFORM =
            "{translation:[-0.045f,0.0f,-0.045f],scale:[0.09f,2.4f,0.09f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}";
    private static final String SAIL_TRANSFORM =
            "{translation:[-0.02f,0.45f,-1.4f],scale:[0.04f,1.85f,1.35f],"
                    + "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}";

    public static Rig rigOf(Entity e) {
        return e == null ? null : RIGS.get(e.getUuid());
    }

    // ------------------------------------------------------------------ setup

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof AbstractBoatEntity boat && boat.getCommandTags().contains(TAG_RIG)) {
                loadFromTags(world, boat);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            Rig r = RIGS.remove(entity.getUuid());
            if (r != null) {
                Entity.RemovalReason reason = entity.getRemovalReason();
                if (reason != null && reason.shouldDestroy()) removeVisuals(r);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(RiggedBoats::tick);
        UseEntityCallback.EVENT.register(RiggedBoats::onUseEntity);
    }

    private static void loadFromTags(ServerWorld world, AbstractBoatEntity boat) {
        Rig r = new Rig(world, boat);
        for (String tag : boat.getCommandTags()) {
            if (tag.startsWith("sl_cls_")) {
                try {
                    r.cls = BoatClass.of(Integer.parseInt(tag.substring(7)));
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            } else if (tag.startsWith("sl_col_")) {
                r.sail = tag.substring(7);
            }
        }
        RIGS.put(boat.getUuid(), r);
    }

    private static Rig rig(ServerWorld world, AbstractBoatEntity boat, BoatClass cls) {
        for (String t : new ArrayList<>(boat.getCommandTags())) {
            if (t.startsWith("sl_cls_")) boat.removeCommandTag(t);
        }
        boat.addCommandTag(TAG_RIG);
        boat.addCommandTag("sl_cls_" + cls.ordinal());

        Rig r = RIGS.get(boat.getUuid());
        if (r == null) {
            r = new Rig(world, boat);
            r.sail = cls.defaultSail;
            boat.addCommandTag("sl_col_" + r.sail);
            RIGS.put(boat.getUuid(), r);
        }
        r.cls = cls;
        return r;
    }

    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity,
                                            EntityHitResult hit) {
        if (world.isClient() || !(world instanceof ServerWorld sw) || !(entity instanceof AbstractBoatEntity boat)) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getStackInHand(hand);
        Rig rig = RIGS.get(boat.getUuid());

        if (stack.getItem() instanceof RigKitItem kit) {
            Rig r = rig(sw, boat, kit.boatClass);
            removeVisuals(r); // rebuilt next tick
            stack.decrementUnlessCreative(1, player);
            player.sendMessage(Text.literal("Rigged as a " + kit.boatClass.label
                    + ". Hold a mainsheet while sailing."), true);
            return ActionResult.SUCCESS;
        }

        if (rig != null && stack.getItem() instanceof DyeItem dye) {
            String color = dye.getColor().getId();
            for (String t : new ArrayList<>(boat.getCommandTags())) {
                if (t.startsWith("sl_col_")) boat.removeCommandTag(t);
            }
            boat.addCommandTag("sl_col_" + color);
            rig.sail = color;
            stack.decrementUnlessCreative(1, player);
            player.sendMessage(Text.literal("New sail colour: " + color.replace('_', ' ')), true);
            return ActionResult.SUCCESS;
        }

        if (rig != null) {
            if (rig.capsized > 0) {
                player.sendMessage(Text.literal("The dinghy is capsized. Give it a few seconds to right itself."), true);
                return ActionResult.FAIL;
            }
            if (!boat.getPassengerList().isEmpty() && !boat.hasPassenger(player)) {
                player.sendMessage(Text.literal("It's a one-person dinghy."), true);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    // ------------------------------------------------------------------ per tick

    private static void tick(MinecraftServer server) {
        if (RIGS.isEmpty()) return;

        double wf = Wind.fromYaw(server);
        double kn = Wind.knots(server);
        double amp = Wind.waveAmp(kn);
        long time = server.getOverworld().getTime();

        for (Rig r : new ArrayList<>(RIGS.values())) {
            AbstractBoatEntity b = r.boat;
            if (b.isRemoved()) {
                RIGS.remove(b.getUuid());
                continue;
            }

            // measured speed
            if (r.hasLast) {
                double dist = Math.hypot(b.getX() - r.lastX, b.getZ() - r.lastZ);
                r.speedKn = r.speedKn * 0.7 + dist * 20 * 1.944 * 0.3;
            }
            r.lastX = b.getX();
            r.lastZ = b.getZ();
            r.hasLast = true;

            // one person only
            List<Entity> pass = new ArrayList<>(b.getPassengerList());
            for (int i = 1; i < pass.size(); i++) pass.get(i).stopRiding();
            Entity rider = pass.isEmpty() ? null : pass.get(0);

            // capsized: sit there until it rights itself
            if (r.capsized > 0) {
                r.capsized--;
                removeVisuals(r);
                Vec3d v = b.getVelocity();
                b.setVelocity(v.x * 0.5, v.y, v.z * 0.5);
                if (r.capsized == 0) r.stress = 0;
                continue;
            }

            double h = b.getYaw();
            double rel = MathHelper.wrapDegrees(wf - h);   // > 0: wind comes from starboard
            double ar = Math.abs(rel);
            if (ar > 3) r.side = rel > 0 ? 1 : -1;
            double opt = SailMath.optTrim(ar);
            boolean luff = ar < 30 || (r.trim > opt + 25 && ar < 100);
            double alpha = Math.min(r.trim, Math.max(5, ar - 8));
            if (luff) alpha += 5 * Math.sin(time * 0.8);
            double sailYaw = h + r.side * alpha;

            // overpowered? (strong gust, beam-on, sheeted in too hard)
            boolean over = kn > r.cls.gustLimit && ar > 55 && ar < 140 && r.trim < opt - 8;
            if (over) r.stress += (kn - r.cls.gustLimit) * 0.35 + 1;
            else r.stress = Math.max(0, r.stress - 1.5);
            if (r.stress > 60) {
                capsize(r, rider);
                continue;
            }

            placeVisuals(r, h, sailYaw);

            if (rider != null) {
                double rad = Math.toRadians(h);
                if (time % 3 == 0 && r.speedKn > 3) {
                    r.world.spawnParticles(ParticleTypes.SPLASH,
                            b.getX() - Math.sin(rad) * 0.8, b.getY() + 0.35, b.getZ() + Math.cos(rad) * 0.8,
                            3, 0.25, 0.05, 0.25, 0.1);
                }
                if (time % 8 == 0 && amp > 0.06) whitecaps(r, amp);
            }

            if (rider instanceof ServerPlayerEntity p) {
                ServerPlayNetworking.send(p, new SailStatePayload(
                        b.getId(), (float) wf, (float) kn, (float) r.trim, r.cls.ordinal(), (float) amp));
                if (time % 4 == 0) {
                    String warn = r.stress > 25 ? "  !! EASE THE SHEET !!" : (luff ? "  (luffing)" : "");
                    p.sendMessage(Text.literal(String.format(Locale.ROOT,
                            "%s | Wind %.0f kn %s | Heading %03.0f | Sheet %.0f (ideal %.0f) | %.1f kn%s",
                            r.cls.label, kn, Wind.compass(wf), Wind.bearing(h), r.trim, opt, r.speedKn, warn)), true);
                }
            }
        }
    }

    private static void capsize(Rig r, Entity rider) {
        r.boat.removeAllPassengers();
        r.capsized = 100;
        removeVisuals(r);
        r.world.spawnParticles(ParticleTypes.SPLASH, r.boat.getX(), r.boat.getY() + 0.5, r.boat.getZ(),
                40, 0.8, 0.3, 0.8, 0.3);
        if (rider instanceof ServerPlayerEntity p) {
            p.sendMessage(Text.literal("CAPSIZED! Ease the sheet in gusts."), true);
        }
    }

    private static void whitecaps(Rig r, double amp) {
        ServerWorld w = r.world;
        int n = 3 + (int) (amp * 14);
        for (int i = 0; i < n; i++) {
            double x = r.boat.getX() + (w.getRandom().nextDouble() - 0.5) * 28;
            double z = r.boat.getZ() + (w.getRandom().nextDouble() - 0.5) * 28;
            double y = r.boat.getY();
            if (w.getFluidState(BlockPos.ofFloored(x, y - 0.2, z)).isIn(FluidTags.WATER)) {
                w.spawnParticles(ParticleTypes.SPLASH, x, y + 0.05, z, 1, 0.1, 0.0, 0.1, 0.02);
            }
        }
    }

    // ------------------------------------------------------------------ sail visuals

    private static void placeVisuals(Rig r, double h, double sailYaw) {
        if (r.visFailures >= 3) return;
        ensureVisuals(r);
        double rad = Math.toRadians(h);
        double mx = r.boat.getX() - Math.sin(rad) * 0.35;
        double mz = r.boat.getZ() + Math.cos(rad) * 0.35;
        double my = r.boat.getY() + 0.15;

        Entity mast = r.mast == null ? null : r.world.getEntity(r.mast);
        if (mast != null) mast.setPosition(mx, my, mz);
        Entity sail = r.sailVis == null ? null : r.world.getEntity(r.sailVis);
        if (sail != null) {
            sail.setPosition(mx, my, mz);
            sail.setYaw((float) sailYaw);
        }
    }

    private static boolean alive(Rig r, UUID id) {
        if (id == null) return false;
        Entity e = r.world.getEntity(id);
        return e != null && !e.isRemoved();
    }

    private static void ensureVisuals(Rig r) {
        if (!r.sail.equals(r.visSail)) {
            removeVisuals(r);
            r.visSail = r.sail;
        }
        if (!alive(r, r.mast)) {
            r.mast = spawnOrFind(r, "mast", "minecraft:stripped_birch_log", MAST_TRANSFORM);
        }
        if (!alive(r, r.sailVis)) {
            r.sailVis = spawnOrFind(r, "sail", "minecraft:" + r.sail + "_wool", SAIL_TRANSFORM);
        }
        if (r.mast == null || r.sailVis == null) {
            r.visFailures++;
            if (r.visFailures == 3) {
                Sailing.LOGGER.warn("Could not create sail display entities; boats will sail without a visible rig.");
            }
        } else {
            r.visFailures = 0;
        }
    }

    private static List<DisplayEntity.BlockDisplayEntity> findByTag(Rig r, String tag) {
        double x = r.boat.getX(), y = r.boat.getY(), z = r.boat.getZ();
        return r.world.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class,
                new Box(x - 6, y - 6, z - 6, x + 6, y + 8, z + 6), e -> e.getCommandTags().contains(tag));
    }

    private static UUID spawnOrFind(Rig r, String kind, String blockId, String transformation) {
        String tag = "sl_vis_" + kind + "_" + r.boat.getUuid();
        List<DisplayEntity.BlockDisplayEntity> existing = findByTag(r, tag);
        if (!existing.isEmpty()) return existing.get(0).getUuid();

        try {
            String cmd = String.format(Locale.ROOT,
                    "summon minecraft:block_display %.3f %.3f %.3f {Tags:[\"sl_vis\",\"%s\"],"
                            + "block_state:{Name:\"%s\"},teleport_duration:2,view_range:2.0f,transformation:%s}",
                    r.boat.getX(), r.boat.getY(), r.boat.getZ(), tag, blockId, transformation);
            MinecraftServer server = r.world.getServer();
            ServerCommandSource src = server.getCommandSource().withWorld(r.world).withSilent();
            server.getCommandManager().executeWithPrefix(src, cmd);
        } catch (Exception e) {
            Sailing.LOGGER.warn("Display summon failed", e);
            return null;
        }
        List<DisplayEntity.BlockDisplayEntity> made = findByTag(r, tag);
        return made.isEmpty() ? null : made.get(0).getUuid();
    }

    private static void removeVisuals(Rig r) {
        for (String kind : new String[]{"mast", "sail"}) {
            String tag = "sl_vis_" + kind + "_" + r.boat.getUuid();
            for (DisplayEntity.BlockDisplayEntity e : findByTag(r, tag)) e.discard();
        }
        r.mast = null;
        r.sailVis = null;
    }
}
