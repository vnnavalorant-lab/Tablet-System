# Tablet Inception (Fabric 1.20.1)

Item **Tablet**: clique direito abre uma tela que mostra e controla um Minecraft rodando em outro processo.

## Compilar pelo GitHub
1. Crie um repositorio e envie todos os arquivos (inclusive a pasta `.github`).
2. Aba **Actions** -> **build** -> baixe o `.jar` em **Artifacts**.
3. Coloque o jar na pasta `mods` junto com o Fabric API 0.92.2+1.20.1 (Minecraft 1.20.1, Java 17).

## Usar
- Receita: ferro (borda), vidro fino (centro), redstone (embaixo), ou `/give @s tablet_inception:tablet`.
- Clique direito com o tablet: abre a tela inicial, com o icone do **Minecraft**.
- Toque no icone: abre o Minecraft interno (liga o jogo; a primeira vez demora).
- No app: **clique na tela** para capturar mouse e teclado, **Ctrl direito** solta. Com tudo solto, **Esc** fecha o tablet.
- Botoes da coluna direita do tablet:
  - triangulo: voltar (no app volta ao inicio; no inicio fecha o tablet)
  - circulo: tela inicial (o Minecraft continua rodando em segundo plano)
  - quadrado: fechar o app (desliga o Minecraft interno)

## Como funciona
- O jogo interno e um segundo processo Java com a mesma classpath, uma pasta propria
  (`<jogo>/tablet_inception/instance`, com mundos e opcoes separados), conta offline e este mod + Fabric API.
- No jogo interno, mixins deixam a janela oculta, leem o framebuffer a cada frame (`glReadPixels`) e
  gravam em um arquivo mapeado em memoria (`tablet_inception/channel.bin`).
- A tela do tablet le esse arquivo, sobe os pixels para uma textura e envia teclado/mouse de volta por
  um anel de eventos no mesmo arquivo.

## Configuracao (`config/tablet_inception.json`)
`width`, `height` (resolucao do jogo interno; padrao 800x570, mesma proporcao da tela do tablet), `memoryMb`, `fps` (captura), `username`,
`javaPath`, `extraJvmArgs`.

## Problemas
- Log do jogo interno: `<jogo>/tablet_inception/child.log`.
- Nao funciona em `gradle runClient` (precisa do .jar em um launcher).
- Launchers que nao usam a classpath padrao do Fabric (alguns como Prism/MultiMC) podem exigir ajustes.
- Ctrl+C/V e outros atalhos que consultam o estado do teclado direto no GLFW nao funcionam no jogo interno.
- O jogo interno e offline: so singleplayer.
