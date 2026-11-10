# Tablet Inception (Fabric 1.20.1)

**Tablet** item: right-click opens a screen that shows and controls a Minecraft running in another process. The mod is translated into **English (en_us)** and **Portuguese (pt_br)**, and the tablet UI uses a **dark theme by default**.

## Build with GitHub
1. Create a repository and upload all files (including the `.github` folder).
2. **Actions** tab -> **build** -> download the `.jar` from **Artifacts**.
3. Put the jar in your `mods` folder together with Fabric API 0.92.2+1.20.1 (Minecraft 1.20.1, Java 17).

## Usage
- Recipe: iron (border), thin glass (center), redstone (bottom), or `/give @s tablet_inception:tablet`.
- Right-click with the tablet: opens the home screen with the **Minecraft** icon. Tap it to start the inner Minecraft (the first start takes a while).
- Inside the app: **click the screen** to capture mouse and keyboard, **Right Ctrl** releases them. With everything released, **Esc** closes the tablet.
- Right-hand buttons: triangle = back, circle = home (Minecraft keeps running), square = close the app (shuts the inner Minecraft down).

## Apps
- **ScuteAI**: simple keyword-based chat assistant (math like `2+2`, time, date, jokes, Minecraft tips). Replies follow the game language; edit `RULES_EN` / `RULES_PT` in `client/ScuteAi.java` to add answers.
- **Clock**, **Music** (Minecraft music discs), **Notes** (auto-saved to `config/tablet_inception/notes.txt`), **Weather** (reads the real in-game weather, with an animated scene and comments).
- **Camera**, **Gallery**, **Books** and **App Store** show "App under development ;(".
- **Settings**: wallpaper (pick or drag an image onto the tablet) and colors of the top/side panels (the default is dark).

## Languages
Texts live in `assets/tablet_inception/lang/en_us.json` and `pt_br.json` and are read in code with `Lang.t("key")`. To add a language, copy `en_us.json` to e.g. `es_es.json` and translate the values (ScuteAI keyword rules fall back to English).

## Configuration (`config/tablet_inception.json`)
`width` (0 = automatic), `height` (inner game height, default 480), `memoryMb`, `fps`, `username`, `javaPath`, `extraJvmArgs`.

## Troubleshooting
- Inner game log: `<game>/tablet_inception/child.log`.
- Does not work with `gradle runClient` (needs the .jar in a launcher).
- Some launchers that don't use the default Fabric classpath (Prism/MultiMC) may need adjustments.
- Ctrl+C/V and shortcuts that read the keyboard state straight from GLFW don't work inside the inner game. The inner game is offline (singleplayer only).
