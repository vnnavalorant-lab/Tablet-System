# Minecraft Inside Minecraft (Fabric 1.20.1)

Tablet que roda uma versão simplificada e jogável de Minecraft **dentro** do jogo, sem segunda instância, sem outro processo Java.

## Como compilar
1. JDK 17.
2. Dentro da pasta: `gradle wrapper --gradle-version 8.6` (uma vez) e depois `./gradlew build`.
   (Ou copie `src/` para um projeto novo do gerador em https://fabricmc.net/develop — confira lá as versões de loom/yarn/Fabric API.)
3. O .jar sai em `build/libs/`. Coloque em `mods/` com o Fabric API.
4. Para testar no ambiente de dev: `./gradlew runClient`.

## Como usar
- Receita: ferro/vidraça/redstone (ver `data/.../recipes/tablet.json`) ou pegue na aba Ferramentas do criativo.
- Clique direito com o tablet na mão abre. Agachado + clique em um bloco: coloca o tablet no mundo; clique direito nele abre.
- App **Minecraft**: WASD, mouse, Espaço (pular), botão esquerdo quebra, direito coloca, roda do mouse/1-9 troca de bloco, ESC sai.
- App **Configurações**: resolução, FPS interno, distância, entidades, iluminação, criaturas, modo economia.

## Arquitetura (pacote dev.mcinside)
| Módulo pedido | Classe |
|---|---|
| TabletSystem | `TabletSystem`, `TabletItem`, `TabletBlock`, `client/TabletScreen` |
| VirtualWorld | `virtual/VirtualWorld` |
| VirtualRenderer | `virtual/VirtualRenderer` (raycaster DDA na CPU → `NativeImageBackedTexture`) |
| VirtualChunkManager | `virtual/VirtualChunkManager` |
| VirtualPlayer | `virtual/VirtualPlayer` + `VirtualBody` (física/colisão) |
| VirtualInventory | `virtual/VirtualInventory` |
| VirtualEntityManager | `virtual/VirtualEntityManager`, `VirtualEntity` |
| PerformanceManager | `virtual/PerformanceManager` |
| InputManager | `client/InputManager` |

O pacote `virtual` **não importa nada do Minecraft**. Dá para testá-lo sem o jogo: `tools/run-core-test.sh`.

## Decisões de design
- **CPU raycasting em vez de Framebuffer/FBO do OpenGL**: não duplica nenhum sistema de renderização do jogo e não depende de APIs de GL internas que mudam entre versões. A "RenderTexture" é uma textura dinâmica 854x480 reutilizada; resoluções menores usam só uma sub-região dela (sem recriar texturas).
- **Frustum culling**: só se lançam raios dentro do campo de visão. **Distance culling**: alcance do raio = distância em chunks, com neblina escondendo o corte.
- **Resolução dinâmica**: se o frame passa de ~70% do orçamento de tempo, a resolução desce; se sobra muito, sobe (até o valor escolhido).
- **Suspensão**: a simulação e o render só rodam dentro do método `render` da tela em modo jogo. Fechou o tablet, nada roda, e os chunks não editados são liberados.
- Texturas dos blocos são **procedurais** (não usam o atlas do jogo), por isso o visual é "parecido", não idêntico ao vanilla.

## Limitações conhecidas
- Mundo interno não é salvo em disco (as construções vivem enquanto o jogo estiver aberto).
- Sem água, cavernas, minérios, dano ou combate; criaturas só vagam.
- O render é por CPU: em 512x288 e 854x480 usa threads paralelas; em PCs fracos a resolução dinâmica reduz sozinha.
- Sem áudio.
