package dev.mcinside.client;

import dev.mcinside.TabletSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

public class MinecraftInsideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TabletSystem.openHandler = () -> MinecraftClient.getInstance().setScreen(new TabletScreen());
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.currentScreen instanceof TabletScreen t) t.tickInner();
        });
    }
}
