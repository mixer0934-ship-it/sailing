package com.example.sailing;

import net.minecraft.item.Item;

/** Right-click a boat with this to turn it into a one-person dinghy. Handled in RiggedBoats. */
public class RigKitItem extends Item {
    public final BoatClass boatClass;

    public RigKitItem(Settings settings, BoatClass boatClass) {
        super(settings);
        this.boatClass = boatClass;
    }
}
