package dev.mcinside.client;

import dev.mcinside.TabletSystem;
import dev.mcinside.virtual.PerformanceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public class MinecraftInsideClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PerformanceManager.INSTANCE.init(FabricLoader.getInstance().getConfigDir().resolve("minecraftinside.properties"));
        TabletSystem.openHandler = () -> MinecraftClient.getInstance().setScreen(new TabletScreen());
    }
}
