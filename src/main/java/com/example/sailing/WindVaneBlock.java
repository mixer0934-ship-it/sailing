package com.example.sailing;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Its arrow points into the wind. Updates itself every half second with a scheduled tick. */
public class WindVaneBlock extends Block {
    public static final IntProperty ROTATION = Properties.ROTATION;

    public WindVaneBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ROTATION, 0));
    }

    @Override
    public void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleBlockTick(pos, this, 5);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        double from = Wind.fromYaw(world.getServer());
        int k = ((int) Math.round(from / 22.5)) & 15;
        if (state.get(ROTATION) != k) {
            world.setBlockState(pos, state.with(ROTATION, k), Block.NOTIFY_LISTENERS);
        }
        world.scheduleBlockTick(pos, this, 10);
    }
}
