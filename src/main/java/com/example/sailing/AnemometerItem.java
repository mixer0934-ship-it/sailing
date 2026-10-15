package com.example.sailing;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class AnemometerItem extends Item {
    public AnemometerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld sw) {
            double from = Wind.fromYaw(sw.getServer());
            double kn = Wind.knots(sw.getServer());
            user.sendMessage(Text.literal(String.format(
                    "Wind %.1f knots from the %s (%.0f deg)  |  waves ~%.1f m",
                    kn, Wind.compass(from), Wind.bearing(from), Wind.waveAmp(kn) * 2)), true);
        }
        return ActionResult.SUCCESS;
    }
}
