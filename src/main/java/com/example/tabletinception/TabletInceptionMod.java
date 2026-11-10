package com.example.tabletinception;

import com.example.tabletinception.item.TabletItem;
import com.example.tabletinception.server.TabletServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TabletInceptionMod implements ModInitializer {
    public static final String MOD_ID = "tablet_inception";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item TABLET = new TabletItem(new FabricItemSettings().maxCount(1));

    /**
     * Definido pelo codigo do cliente. Assim a classe do item nunca referencia
     * classes exclusivas do cliente (seguro para servidores dedicados).
     */
    public static volatile Runnable screenOpener = null;

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "tablet"), TABLET);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(TABLET));
        TabletServer.init();
        LOGGER.info("Ipad-Scute6o carregado.");
    }
}
