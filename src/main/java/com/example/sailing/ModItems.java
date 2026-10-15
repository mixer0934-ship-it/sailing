package com.example.sailing;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

import java.util.function.Function;

public final class ModItems {
    private ModItems() {}

    public static Item OPTIMIST_RIG, LASER_RIG, SKIFF_RIG;
    public static Item MAINSHEET, ANEMOMETER, RACE_CLIPBOARD, LIFE_JACKET;
    public static Item MARK_BUOY, START_BUOY, GATE_BUOY, WIND_VANE;

    private static Item reg(String id, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Sailing.id(id));
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    public static void register() {
        OPTIMIST_RIG = reg("optimist_rig", s -> new RigKitItem(s, BoatClass.OPTIMIST), new Item.Settings().maxCount(16));
        LASER_RIG = reg("laser_rig", s -> new RigKitItem(s, BoatClass.LASER), new Item.Settings().maxCount(16));
        SKIFF_RIG = reg("skiff_rig", s -> new RigKitItem(s, BoatClass.SKIFF), new Item.Settings().maxCount(16));

        MAINSHEET = reg("mainsheet", MainsheetItem::new, new Item.Settings().maxCount(1));
        ANEMOMETER = reg("anemometer", AnemometerItem::new, new Item.Settings().maxCount(1));
        RACE_CLIPBOARD = reg("race_clipboard", RaceClipboardItem::new, new Item.Settings().maxCount(1));
        LIFE_JACKET = reg("life_jacket", Item::new,
                new Item.Settings().maxCount(1).equippable(EquipmentSlot.CHEST));

        MARK_BUOY = reg("mark_buoy", s -> new BuoyItem(ModBlocks.MARK_BUOY, s),
                new Item.Settings().useBlockPrefixedTranslationKey());
        START_BUOY = reg("start_buoy", s -> new BuoyItem(ModBlocks.START_BUOY, s),
                new Item.Settings().useBlockPrefixedTranslationKey());
        GATE_BUOY = reg("gate_buoy", s -> new BuoyItem(ModBlocks.GATE_BUOY, s),
                new Item.Settings().useBlockPrefixedTranslationKey());
        WIND_VANE = reg("wind_vane", s -> new net.minecraft.item.BlockItem(ModBlocks.WIND_VANE, s),
                new Item.Settings().useBlockPrefixedTranslationKey());

        RegistryKey<ItemGroup> groupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, Sailing.id("main"));
        Registry.register(Registries.ITEM_GROUP, groupKey, FabricItemGroup.builder()
                .icon(() -> new ItemStack(LASER_RIG))
                .displayName(Text.translatable("itemGroup." + Sailing.MOD_ID))
                .entries((ctx, entries) -> {
                    entries.add(OPTIMIST_RIG);
                    entries.add(LASER_RIG);
                    entries.add(SKIFF_RIG);
                    entries.add(MAINSHEET);
                    entries.add(ANEMOMETER);
                    entries.add(WIND_VANE);
                    entries.add(MARK_BUOY);
                    entries.add(START_BUOY);
                    entries.add(GATE_BUOY);
                    entries.add(RACE_CLIPBOARD);
                    entries.add(LIFE_JACKET);
                })
                .build());
    }
}
