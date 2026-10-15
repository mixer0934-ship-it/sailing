package com.example.sailing;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> rider, every tick: everything the client needs to apply sailing forces to the boat
 * it is steering. flags: bits 0-3 = boat class, bit 4 = capsized.
 */
public record SailStatePayload(int boatId, float windFrom, float knots, float trim, int flags, float waveAmp)
        implements CustomPayload {

    public static final CustomPayload.Id<SailStatePayload> ID =
            new CustomPayload.Id<>(Sailing.id("sail_state"));

    public static final PacketCodec<RegistryByteBuf, SailStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, SailStatePayload::boatId,
            PacketCodecs.FLOAT, SailStatePayload::windFrom,
            PacketCodecs.FLOAT, SailStatePayload::knots,
            PacketCodecs.FLOAT, SailStatePayload::trim,
            PacketCodecs.INTEGER, SailStatePayload::flags,
            PacketCodecs.FLOAT, SailStatePayload::waveAmp,
            SailStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
