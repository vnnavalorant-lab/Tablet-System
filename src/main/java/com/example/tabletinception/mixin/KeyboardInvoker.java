package com.example.tabletinception.mixin;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Keyboard.class)
public interface KeyboardInvoker {
    @Invoker("onKey")
    void tablet$onKey(long window, int key, int scancode, int action, int modifiers);

    @Invoker("onChar")
    void tablet$onChar(long window, int codePoint, int modifiers);
}
