package com.example.tabletinception.net;

import com.example.tabletinception.TabletInceptionMod;
import net.minecraft.util.Identifier;

/** Identificadores dos pacotes (Fabric Networking API). */
public final class TabletNet {
    /** Cliente -> servidor: boolean on (iPad ligado / desligado). */
    public static final Identifier C2S_STATE = new Identifier(TabletInceptionMod.MOD_ID, "state");
    /** Cliente -> servidor: float x, float y (0..1 dentro da tela), boolean tocando. */
    public static final Identifier C2S_CURSOR = new Identifier(TabletInceptionMod.MOD_ID, "cursor");
    /** Servidor -> clientes que veem o jogador: UUID, boolean on, float x, float y, boolean tocando. */
    public static final Identifier S2C_SYNC = new Identifier(TabletInceptionMod.MOD_ID, "sync");

    private TabletNet() {
    }
}
