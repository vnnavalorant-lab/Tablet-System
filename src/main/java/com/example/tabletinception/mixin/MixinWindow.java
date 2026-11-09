package com.example.tabletinception.mixin;

import com.example.tabletinception.client.child.ChildAgent;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Window.class)
public class MixinWindow {
    /** No jogo interno, a janela GLFW e criada oculta. */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
                    remap = false
            )
    )
    private long tablet$createWindow(int width, int height, CharSequence title, long monitor, long share) {
        if (ChildAgent.isChild()) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
        return GLFW.glfwCreateWindow(width, height, title, monitor, share);
    }
}
