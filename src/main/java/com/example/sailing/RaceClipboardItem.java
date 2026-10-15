package com.example.sailing;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Race control.
 *  - Right-click a buoy: add it to your course (in order).
 *  - Sneak + right-click a buoy: remove the last mark.
 *  - Right-click the air: start the race (or cancel a running one).
 *  - Sneak + right-click the air: clear the course.
 */
public class RaceClipboardItem extends Item {
    public RaceClipboardItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();
        if (player == null || !(world.getBlockState(pos).getBlock() instanceof BuoyBlock)) return ActionResult.PASS;

        if (!world.isClient()) {
            List<BlockPos> course = Race.course(player.getUuid());
            if (player.isSneaking()) {
                if (!course.isEmpty()) course.remove(course.size() - 1);
                player.sendMessage(Text.literal("Removed last mark. Course has " + course.size() + " marks."), true);
            } else if (course.size() >= 12) {
                player.sendMessage(Text.literal("A course can have at most 12 marks."), true);
            } else {
                course.add(pos.toImmutable());
                player.sendMessage(Text.literal("Mark " + course.size() + " added."), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
            if (user.isSneaking()) {
                Race.course(user.getUuid()).clear();
                user.sendMessage(Text.literal("Course cleared."), true);
            } else {
                Race.startOrCancel(sw, sp);
            }
        }
        return ActionResult.SUCCESS;
    }
}
