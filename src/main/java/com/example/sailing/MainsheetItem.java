package com.example.sailing;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** The sail control. Right-click to ease the sheet out, sneak + right-click to pull it in. */
public class MainsheetItem extends Item {
    public MainsheetItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            Entity vehicle = user.getVehicle();
            RiggedBoats.Rig rig = RiggedBoats.rigOf(vehicle);
            if (rig == null) {
                user.sendMessage(Text.literal("Hold the mainsheet while sitting in a rigged dinghy."), true);
            } else {
                double delta = user.isSneaking() ? -8 : 8;
                rig.trim = Math.max(0, Math.min(90, rig.trim + delta));
            }
        }
        return ActionResult.SUCCESS;
    }
}
