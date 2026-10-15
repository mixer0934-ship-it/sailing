package com.example.sailing;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sailing implements ModInitializer {
    public static final String MOD_ID = "sailing";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        Payloads.register();
        RiggedBoats.register();
        Race.register();
        LifeJackets.register();
        WindCommand.register();
    }
}
