package com.example.sailing;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class Payloads {
    private Payloads() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SailStatePayload.ID, SailStatePayload.CODEC);
    }
}
