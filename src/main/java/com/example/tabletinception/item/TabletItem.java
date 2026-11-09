package com.example.tabletinception.item;

import com.example.tabletinception.TabletInceptionMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class TabletItem extends Item {
    public TabletItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            Runnable opener = TabletInceptionMod.screenOpener;
            if (opener != null) {
                opener.run();
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
