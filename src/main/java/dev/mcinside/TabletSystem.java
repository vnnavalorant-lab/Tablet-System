package dev.mcinside;

import dev.mcinside.virtual.VirtualWorld;

import java.util.Random;

/**
 * Ponto de contato entre o lado comum (bloco/item) e o cliente.
 * O handler é definido apenas no cliente, então o servidor dedicado nunca carrega classes de GUI.
 */
public final class TabletSystem {
    /** Definido por MinecraftInsideClient. */
    public static volatile Runnable openHandler = () -> { };
    private static VirtualWorld world;

    private TabletSystem() { }

    public static void open() { openHandler.run(); }

    /** O mundo interno vive enquanto o jogo estiver aberto (as construções persistem na sessão). */
    public static VirtualWorld world() {
        if (world == null) world = new VirtualWorld(new Random().nextLong());
        return world;
    }

    public static boolean hasWorld() { return world != null; }
}
