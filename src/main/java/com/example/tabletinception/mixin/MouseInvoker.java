package com.example.tabletinception.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseInvoker {
    @Invoker("onMouseButton")
    void tablet$onMouseButton(long window, int button, int action, int mods);

    @Invoker("onCursorPos")
    void tablet$onCursorPos(long window, double x, double y);

    @Invoker("onMouseScroll")
    void tablet$onMouseScroll(long window, double horizontal, double vertical);

    @Accessor("x")
    double tablet$getX();

    @Accessor("y")
    double tablet$getY();
}
