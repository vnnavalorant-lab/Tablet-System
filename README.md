# Tablet Inception (Fabric 1.20.1)

Item **Tablet**: clique direito abre uma tela que mostra e controla um Minecraft rodando em outro processo.

## Compilar pelo GitHub
1. Crie um repositorio e envie todos os arquivos (inclusive a pasta `.github`).
2. Aba **Actions** -> **build** -> baixe o `.jar` em **Artifacts**.
3. Coloque o jar na pasta `mods` junto com o Fabric API 0.92.2+1.20.1 (Minecraft 1.20.1, Java 17).

## Usar
- Receita: ferro (borda), vidro fino (centro), redstone (embaixo), ou `/give @s tablet_inception:tablet`.
- Clique direito com o tablet: abre a tela inicial (o tablet ocupa quase toda a janela; o jogo preenche toda a tela, por baixo da moldura translucida), com o icone do **Minecraft**.
- Toque no icone: abre o Minecraft interno (liga o jogo; a primeira vez demora).
- No app: **clique na tela** para capturar mouse e teclado, **Ctrl direito** solta. Com tudo solto, **Esc** fecha o tablet.
- Botoes da coluna direita do tablet:
  - triangulo: voltar (no app volta ao inicio; no inicio fecha o tablet)
  - circulo: tela inicial (o Minecraft continua rodando em segundo plano)
  - quadrado: fechar o app (desliga o Minecraft interno)

## ScuteAI (app de chat)
- Quarto icone da tela inicial (o logo da ScuteAI; a skin da ScuteAI aparece so dentro do chat). Abre um chat: suas mensagens ficam a **direita** com o seu rosto (skin do dono do tablet) e as respostas da **ScuteAI** a esquerda com o rosto da skin dela (`textures/gui/scute_skin.png`).
- Digite e aperte **Enter**. Roda do mouse / PageUp / PageDown rolam o historico. **Esc** ou o triangulo voltam ao inicio. Ctrl+V cola.
- E uma IA simples: procura palavras-chave e devolve respostas prontas (saudacoes, contas como `2+2`, hora, data, piadas, dicas de Minecraft...). Para adicionar respostas, edite a lista `RULES` em `client/ScuteAi.java`.
- Para trocar o rosto da ScuteAI, substitua `scute_skin.png` (skin 64x64) (rosto no chat) e, se quiser, `scute_ai_icon.png` (icone 20x20 da tela inicial).

## Como funciona
- O jogo interno e um segundo processo Java com a mesma classpath, uma pasta propria
  (`<jogo>/tablet_inception/instance`, com mundos e opcoes separados), conta offline e este mod + Fabric API.
- No jogo interno, mixins deixam a janela oculta, leem o framebuffer a cada frame (`glReadPixels`) e
  gravam em um arquivo mapeado em memoria (`tablet_inception/channel.bin`).
- A tela do tablet le esse arquivo, sobe os pixels para uma textura e envia teclado/mouse de volta por
  um anel de eventos no mesmo arquivo.

## Configuracao (`config/tablet_inception.json`)
`width` (0 = automatico: acompanha a proporcao da tela do tablet), `height` (altura do jogo interno, padrao 480), `memoryMb`, `fps` (captura), `username`,
`javaPath`, `extraJvmArgs`.

## Problemas
- Log do jogo interno: `<jogo>/tablet_inception/child.log`.
- Nao funciona em `gradle runClient` (precisa do .jar em um launcher).
- Launchers que nao usam a classpath padrao do Fabric (alguns como Prism/MultiMC) podem exigir ajustes.
- Ctrl+C/V e outros atalhos que consultam o estado do teclado direto no GLFW nao funcionam no jogo interno.
- O jogo interno e offline: so singleplayer.


## Apps extras da tela inicial
Relogio, Crafting, Notas, Livros, Ajustes e Clima (icones 20x20 em `textures/gui/*_icon.png`). Por enquanto so ilustrativos (sem funcao), exceto **Ajustes** e **Clima**. A tela inicial e uma grade que quebra linha conforme a largura.


## Ajustes: papel de parede
Abra o app **Ajustes** -> **Escolher imagem** (seletor de arquivos do sistema) ou arraste uma imagem (png, jpg, bmp, gif) do PC para a tela do tablet. A imagem e reduzida (max. 1280 px), salva em `config/tablet_inception/wallpaper.png` e usada na tela inicial. **Restaurar padrao** volta ao fundo roxo.

## Ajustes: cores da interface
Na aba **Cores** de Ajustes voce escolhe a cor do **painel superior** (barra de status) e do **painel lateral** (botoes de navegacao) entre uma paleta de 10 cores; o quadrado com `x` volta ao padrao. As escolhas ficam em `config/tablet_inception/theme.properties`.


## Clima (app que le o clima do jogo)
Toque no icone **Clima**. O app le o clima **real** do mundo onde voce esta e mostra:
- **Nota**: `BOM` (verde, ceu limpo), `REGULAR` (amarelo, neve ou nublado em bioma seco), `RUIM` (vermelho, chuva ou tempestade) ou `SEM CLIMA` (Nether e Fim).
- Titulo do tempo: Ensolarado, Amanhecer, Entardecer, Noite limpa, Chuva (tambem "Comecando a chover" / "Chuva diminuindo"), Nevando, Tempestade, Nublado...
- Cena animada: sol e lua acompanham a hora do jogo (com a fase da lua), nuvens, chuva, neve, estrelas e raios.
- Hora do jogo e dia, bioma atual e intensidade da chuva/neve.
- **Comentarios** sobre o tempo agora (dormir, monstros, pesca, raios, neve em po, se voce esta sob um teto ou no subsolo...). Roda do mouse, setas ou PageUp/PageDown rolam a lista.
- Biomas secos (deserto, savana, mesa) nao tem chuva: o app avisa "Nublado". Biomas gelados mostram neve.

O icone do Clima na tela inicial ganha uma **bolinha** com a nota do tempo, e a barra de status mostra o clima atual no centro (verde/amarelo/vermelho).

Arquivos: `client/WeatherInfo.java` (leitura do clima e textos dos comentarios), `client/WeatherArt.java` (cena animada) e `drawWeather` em `client/TabletScreen.java`.


## Apps simples (Notas, Relogio, Musica e os "em desenvolvimento")
- **Notas**: area de texto para o jogador anotar. Digite, **Enter** quebra a linha, **Backspace** apaga, **Ctrl+V** cola, roda do mouse / setas rolam. Salva sozinho em `config/tablet_inception/notes.txt` (limite de 4000 caracteres).
- **Relogio**: mostra apenas a hora atual (HH:mm:ss) em letras grandes.
- **Musica**: lista com as musicas do Minecraft (os discos: 13, Cat, Blocks, Chirp, Far, Mall, Mellohi, Stal, Strad, Ward, 11, Wait, Otherside, 5, Pigstep, Relic). Clique em uma para tocar; clicar de novo nela (ou em **Parar**, ou no quadrado de navegacao) para a musica. Se fechar o tablet a musica continua tocando. Para mudar a lista, edite `TRACKS` em `client/MusicPlayer.java`.
- **Camera**, **Galeria**, **Livros** e **App Store**: mostram "Aplicativo em Desenvolvimento ;(".
- Os icones novos (`music_icon.png` e `app_store_icon.png`, 20x20) ficam em `textures/gui/`. **Crafting** continua so ilustrativo.
