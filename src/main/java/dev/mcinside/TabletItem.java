package dev.mcinside;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Segurado na mão: clique direito abre. Agachado + clique em bloco: coloca no mundo. */
public class TabletItem extends BlockItem {
    public TabletItem(Block block, Settings settings) { super(block, settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) TabletSystem.open();
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity p = context.getPlayer();
        if (p != null && p.isSneaking()) return super.useOnBlock(context);
        return ActionResult.PASS; // cai no use() acima
    }
}
