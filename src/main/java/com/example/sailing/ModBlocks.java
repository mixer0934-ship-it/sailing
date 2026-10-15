package com.example.sailing;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;

import java.util.function.Function;

public final class ModBlocks {
    private ModBlocks() {}

    public static Block MARK_BUOY;
    public static Block START_BUOY;
    public static Block GATE_BUOY;
    public static Block WIND_VANE;

    private static Block reg(String id, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Sailing.id(id));
        return Registry.register(Registries.BLOCK, key, factory.apply(settings.registryKey(key)));
    }

    private static AbstractBlock.Settings buoySettings() {
        return AbstractBlock.Settings.create().strength(0.6f).nonOpaque().sounds(BlockSoundGroup.WOOL);
    }

    public static void register() {
        MARK_BUOY = reg("mark_buoy", BuoyBlock::new, buoySettings());
        START_BUOY = reg("start_buoy", BuoyBlock::new, buoySettings());
        GATE_BUOY = reg("gate_buoy", BuoyBlock::new, buoySettings());
        WIND_VANE = reg("wind_vane", WindVaneBlock::new,
                AbstractBlock.Settings.create().strength(0.8f).nonOpaque().sounds(BlockSoundGroup.METAL));
    }
}
