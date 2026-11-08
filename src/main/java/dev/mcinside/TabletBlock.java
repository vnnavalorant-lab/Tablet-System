package dev.mcinside;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Tablet apoiado no mundo. Clique direito abre a tela. */
public class TabletBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 3, 14, 2, 13);

    public TabletBlock(Settings settings) { super(settings); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) TabletSystem.open();
        return ActionResult.success(world.isClient);
    }
}
