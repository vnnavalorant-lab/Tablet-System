# Minecraft Inside Minecraft (Fabric 1.20.1)

Tablet que abre um **Minecraft de verdade dentro do Minecraft**: mesmo renderizador, texturas, iluminação, mobs e geração de terreno do jogo, em **Fancy**, sem segunda instância/processo.

## Como funciona
- **Mundo**: uma dimensão runtime (`minecraftinside:tablet_world`, semente própria) criada no servidor integrado e salva dentro do save atual (`dimensions/minecraftinside/tablet_world`). Se a criação falhar, usa um ponto distante do Overworld.
- **Cliente interno**: o jogador "Tablet" entra por uma conexão local (loopback) no mesmo servidor, com o pipeline vanilla de pacotes (chunks, entidades, inventário, predição).
- **Render**: um segundo `WorldRenderer` + `InGameHud` desenham num framebuffer; o `MinecraftClient` tem seus campos trocados só durante tick/render/pacotes internos (`InnerSession.inContext`). O mundo principal não é desenhado enquanto o tablet está aberto.
- **Menus**: título, seleção de mundo, carregamento e pausa são falsos (`FakeScreens`). Inventário/morte são telas vanilla abertas dentro do tablet.
- Gráficos forçados para **Fancy** enquanto o tablet está aberto (restaura ao sair).

## Uso
Receita (ferro/vidraça/redstone) ou aba Ferramentas. Clique direito abre. WASD, mouse, Espaço, Shift, botões esq/dir, roda/1-9, E (inventário), Q, F. ESC = menu de pausa; ESC no título fecha o tablet.

## Build
JDK 17, `gradle wrapper --gradle-version 8.6`, `./gradlew build` (ou o workflow em `.github/workflows`). Coloque o jar + Fabric API em `mods/`.

## Limitações
- **Não foi compilado nem testado** na criação (sem acesso ao Maven da Mojang/Fabric). Nomes Yarn/assinaturas podem exigir ajustes pequenos.
- Só em mundo single-player (precisa do servidor integrado). Incompatível com Sodium/Iris.
- Hora/clima do mundo interno são os do Overworld; sem chat/F3/F5 internos; sons internos usam a posição da câmera interna.
