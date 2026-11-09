package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class TabletInceptionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TabletInceptionMod.screenOpener = () -> MinecraftClient.getInstance().setScreen(new TabletScreen());
    }
}
