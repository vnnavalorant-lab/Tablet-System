package com.example.tabletinception.client;

import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.client.child.ChildAgent;
import com.example.tabletinception.client.ipad.IpadItemRenderer;
import com.example.tabletinception.client.ipad.TabletClientState;
import net.fabricmc.api.ClientModInitializer;

public class TabletInceptionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (ChildAgent.isChild()) {
            // Somos o Minecraft interno: so captura frames e recebe entrada.
            ChildAgent.init();
            return;
        }
        TabletClientState.init();
        IpadItemRenderer.init();
        // clique direito: toca a animacao "play" do iPad na mao e so depois abre a tela do tablet
        TabletInceptionMod.screenOpener = TabletClientState::requestOpen;
    }
}
