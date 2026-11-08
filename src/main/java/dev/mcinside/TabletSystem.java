package dev.mcinside;

/** Ponto de contato entre bloco/item (comum) e o cliente. O handler só é definido no cliente. */
public final class TabletSystem {
    public static volatile Runnable openHandler = () -> { };
    private TabletSystem() { }
    public static void open() { openHandler.run(); }
}
