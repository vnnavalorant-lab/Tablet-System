package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.client.child.ChildAgent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class TabletInceptionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (ChildAgent.isChild()) {
            // Somos o Minecraft interno: so captura frames e recebe entrada.
            ChildAgent.init();
            return;
        }
        TabletInceptionMod.screenOpener = () -> MinecraftClient.getInstance().setScreen(new TabletScreen());
    }
}
