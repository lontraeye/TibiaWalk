![stalker](tibia-stalker.gif)
![magex](tibia-ezgif.com-gif-maker.gif)

# TibiaWalk

Gera imagens e GIFs de outfits do Tibia (addons, cores, montaria, direção) direto dos assets do cliente oficial.

## Requisitos

- Java 21
- Cliente Tibia instalado e atualizado. O que o TibiaWalk usa é a pasta `assets`, normalmente em
  `%LOCALAPPDATA%\Tibia\packages\Tibia\assets` (ou dentro da pasta onde o launcher foi instalado).

Os sprites são da CipSoft e não fazem parte deste repositório.

## CLI

```bash
./gradlew :cli:fatJar                       # gera cli/build/libs/tibiawalk.jar
export TIBIA_ASSETS="C:/.../Tibia/packages/Tibia/assets"   # ou use --assets em cada comando

java -jar cli/build/libs/tibiawalk.jar render -l 130 -a 3 --head ff0000 --body 94 --legs 0055ff --feet 76 -m 651 -d east
java -jar cli/build/libs/tibiawalk.jar render -l 128 -a 1 --idle -o knight.png
java -jar cli/build/libs/tibiawalk.jar info 128
java -jar cli/build/libs/tibiawalk.jar list --mountable --with-addons
```

- Cores: índice da paleta do jogo (`0`–`132`, o mesmo que `lookHead` etc. no servidor) ou hex (`ff0000`).
- Addons: `0` nenhum, `1` primeiro, `2` segundo, `3` ambos.
- Sem `-o`, a saída vai para `generated/`.

## Módulos

- `core`: leitura do `catalog-content.json`, `appearances.dat` (protobuf) e das folhas `sprites-*.bmp.lzma`; renderização e GIF.
- `cli`: linha de comando.
