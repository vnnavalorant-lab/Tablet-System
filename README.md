# Tablet Inception (Fabric 1.20.1)

Etapa 1: item **Tablet** que abre uma tela com uma textura dinamica de teste.
Nas proximas etapas essa tela passa a mostrar um Minecraft rodando em outro processo.

## Compilar pelo GitHub (sem instalar nada)
1. Crie um repositorio e envie todos os arquivos (incluindo a pasta `.github`).
2. Aba **Actions** -> workflow **build** -> espere terminar.
3. Baixe o `.jar` em **Artifacts**.

## Compilar localmente
Requer JDK 17 e Gradle 8.5+:

    gradle build
    gradle runClient     # testar no jogo

O jar fica em `build/libs/`. Para gerar o wrapper: `gradle wrapper --gradle-version 8.5`.

## Usar
Receita: ferro (borda), vidro fino (centro), redstone (embaixo). Ou `/give @s tablet_inception:tablet`.
Clique direito com o item na mao para abrir o tablet.
