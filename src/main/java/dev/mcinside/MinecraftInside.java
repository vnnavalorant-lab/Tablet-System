package dev.mcinside;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class MinecraftInside implements ModInitializer {
    public static final String MOD_ID = "minecraftinside";

    public static final Block TABLET_BLOCK = Registry.register(Registries.BLOCK,
            new Identifier(MOD_ID, "tablet"), new TabletBlock(FabricBlockSettings.create().strength(0.5f).nonOpaque()));
    public static final Item TABLET_ITEM = Registry.register(Registries.ITEM,
            new Identifier(MOD_ID, "tablet"), new TabletItem(TABLET_BLOCK, new FabricItemSettings().maxCount(1)));

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(TABLET_ITEM));
    }
}
