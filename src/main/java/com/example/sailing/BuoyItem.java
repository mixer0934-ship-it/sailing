package com.example.sailing;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Buoys can only be dropped onto water: aim at the sea and right-click. */
public class BuoyItem extends BlockItem {
    public BuoyItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.PASS; // never place on the seabed; use() handles water
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        BlockHitResult hit = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) return ActionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        if (!world.getFluidState(pos).isIn(FluidTags.WATER) || !world.getFluidState(pos).isStill()) {
            return ActionResult.PASS;
        }
        if (!world.isClient()) {
            world.setBlockState(pos, getBlock().getDefaultState(), Block.NOTIFY_ALL);
            ItemStack stack = user.getStackInHand(hand);
            stack.decrementUnlessCreative(1, user);
        }
        return ActionResult.SUCCESS;
    }
}
