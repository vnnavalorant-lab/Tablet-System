package dev.mcinside.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Menus falsos (título, seleção de mundo, carregamento, pausa) desenhados dentro do tablet. */
final class FakeScreens {
    private FakeScreens() { }

    interface Host {
        void play();
        void quitTablet();
        void backToTitle();
        void resume();
        void openSelect();
        void openTitle();
    }

    private static final Identifier LOGO = new Identifier("textures/gui/title/minecraft.png");
    private static final Identifier EDITION = new Identifier("textures/gui/title/edition.png");

    private abstract static class Base extends Screen {
        final Host host;
        Base(Host host, String title) { super(Text.literal(title)); this.host = host; }
        @Override public boolean shouldCloseOnEsc() { return false; }
        @Override public boolean shouldPause() { return false; }
        ButtonWidget btn(String label, int x, int y, int w, Runnable r, boolean active) {
            ButtonWidget b = ButtonWidget.builder(Text.literal(label), bt -> r.run()).dimensions(x, y, w, 20).build();
            b.active = active;
            return addDrawableChild(b);
        }
    }

    static final class Title extends Base {
        private RotatingCubeMapRenderer pano;
        private float time;
        Title(Host h) { super(h, "Minecraft"); }
        @Override protected void init() {
            try { pano = new RotatingCubeMapRenderer(TitleScreen.PANORAMA_CUBE_MAP); } catch (Throwable t) { pano = null; }
            int x = width / 2 - 100, y = height / 4 + 48;
            btn("Singleplayer", x, y, 200, host::openSelect, true);
            btn("Multiplayer", x, y + 24, 200, () -> { }, false);
            btn("Minecraft Realms", x, y + 48, 200, () -> { }, false);
            btn("Options...", x, y + 72 + 12, 98, () -> { }, false);
            btn("Quit Game", x + 102, y + 72 + 12, 98, host::quitTablet, true);
        }
        @Override public void render(DrawContext c, int mx, int my, float d) {
            time += d;
            boolean ok = false;
            if (pano != null) { try { pano.render(d, 1.0f); ok = true; } catch (Throwable t) { pano = null; } }
            if (!ok) renderBackgroundTexture(c);
            int lx = width / 2 - 137;
            c.drawTexture(LOGO, lx, 30, 0, 0, 155, 44, 256, 64);
            c.drawTexture(LOGO, lx + 155, 30, 0, 45, 155, 44, 256, 64);
            c.drawTexture(EDITION, lx + 88, 67, 0, 0, 98, 14, 128, 16);
            super.render(c, mx, my, d);
            c.drawTextWithShadow(textRenderer, "Minecraft 1.20.1", 2, height - 10, 0xFFFFFF);
            String cp = "Copyright Mojang AB. Do not distribute!";
            c.drawTextWithShadow(textRenderer, cp, width - textRenderer.getWidth(cp) - 2, height - 10, 0xFFFFFF);
        }
    }

    static final class Select extends Base {
        Select(Host h) { super(h, "Select World"); }
        @Override protected void init() {
            btn("Play Selected World", width / 2 - 154, height - 52, 150, host::play, true);
            btn("Create New World", width / 2 + 4, height - 52, 150, host::play, true);
            btn("Back", width / 2 + 4, height - 28, 150, host::openTitle, true);
        }
        @Override public void render(DrawContext c, int mx, int my, float d) {
            renderBackgroundTexture(c);
            c.drawCenteredTextWithShadow(textRenderer, "Select World", width / 2, 12, 0xFFFFFF);
            int x = width / 2 - 110, y = 44;
            c.fill(x - 2, y - 2, x + 222, y + 38, 0xFFFFFFFF);
            c.fill(x, y, x + 220, y + 36, 0xFF000000);
            c.drawTextWithShadow(textRenderer, "New World", x + 4, y + 3, 0xFFFFFF);
            c.drawTextWithShadow(textRenderer, "Survival Mode, Cheats: Off, Version: 1.20.1", x + 4, y + 14, 0x808080);
            c.drawTextWithShadow(textRenderer, "(minecraftinside)", x + 4, y + 25, 0x808080);
            super.render(c, mx, my, d);
        }
    }

    static final class Loading extends Base {
        final long t0 = System.currentTimeMillis();
        Loading(Host h) { super(h, "Loading"); }
        @Override public void render(DrawContext c, int mx, int my, float d) {
            renderBackgroundTexture(c);
            c.drawCenteredTextWithShadow(textRenderer, "Loading terrain...", width / 2, height / 2 - 10, 0xFFFFFF);
            int w = 120, x = width / 2 - w / 2, y = height / 2 + 6;
            c.fill(x - 1, y - 1, x + w + 1, y + 5, 0xFF000000);
            int p = (int) (w * Math.min(1.0, (System.currentTimeMillis() - t0) / 3000.0));
            c.fill(x, y, x + p, y + 4, 0xFF80FF20);
            super.render(c, mx, my, d);
        }
    }

    static final class Pause extends Base {
        Pause(Host h) { super(h, "Game Menu"); }
        @Override protected void init() {
            int x = width / 2 - 102, y = height / 4 + 24;
            btn("Back to Game", x, y, 204, host::resume, true);
            btn("Advancements", x, y + 48, 98, () -> { }, false);
            btn("Statistics", x + 106, y + 48, 98, () -> { }, false);
            btn("Options...", x, y + 96, 98, () -> { }, false);
            btn("Open to LAN", x + 106, y + 96, 98, () -> { }, false);
            btn("Save and Quit to Title", x, y + 120, 204, host::backToTitle, true);
        }
        @Override public void render(DrawContext c, int mx, int my, float d) {
            renderBackground(c);
            c.drawCenteredTextWithShadow(textRenderer, "Game Menu", width / 2, 40, 0xFFFFFF);
            super.render(c, mx, my, d);
        }
    }

    static final class Message extends Base {
        final String msg;
        Message(Host h, String msg) { super(h, "Error"); this.msg = msg; }
        @Override protected void init() { btn("Back", width / 2 - 100, height / 2 + 20, 200, host::openTitle, true); }
        @Override public void render(DrawContext c, int mx, int my, float d) {
            renderBackgroundTexture(c);
            c.drawCenteredTextWithShadow(textRenderer, msg, width / 2, height / 2 - 10, 0xFF5555);
            super.render(c, mx, my, d);
        }
    }
}
