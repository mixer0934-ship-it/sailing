package com.example.sailing;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Wear a life jacket in the chest slot: you float, and you can't drown. */
public final class LifeJackets {
    private LifeJackets() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LifeJackets::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.LIFE_JACKET)) continue;
            if (!p.isTouchingWater() || p.hasVehicle()) continue;

            p.setAir(p.getMaxAir());
            if (!p.isSneaking()) {
                Vec3d v = p.getVelocity();
                if (v.y < 0.03) {
                    p.setVelocity(v.x, 0.03, v.z);
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            }
        }
    }
}
