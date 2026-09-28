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
java -jar cli/build/libs/tibiawalk.jar render -l Citizen --female -a 3 -m "Widow Queen" -d west
java -jar cli/build/libs/tibiawalk.jar render -l 128 -a 1 --idle -o knight.png
java -jar cli/build/libs/tibiawalk.jar info "Midnight Panther"
java -jar cli/build/libs/tibiawalk.jar list --players --female --with-addons
java -jar cli/build/libs/tibiawalk.jar list --mounts --json
java -jar cli/build/libs/tibiawalk.jar list -s dragon
```

- Cores: índice da paleta do jogo (`0`–`132`, o mesmo que `lookHead` etc. no servidor) ou hex (`ff0000`).
- Addons: `0` nenhum, `1` primeiro, `2` segundo, `3` ambos.
- Outfits e montarias aceitam número ou nome; com nome de outfit de player, `--female` escolhe a versão feminina.
- Sem `-o`, a saída vai para `generated/`.

## Desktop

```bash
./gradlew :desktop:fatJar                   # gera desktop/build/libs/tibiawalk-desktop.jar
java -jar desktop/build/libs/tibiawalk-desktop.jar   # ou duplo clique no jar
```

Na primeira vez ele procura a pasta do cliente (`TIBIA_ASSETS`, `%LOCALAPPDATA%\Tibia\...`) e, se não achar,
pergunta. Pode apontar para a pasta de instalação do launcher, não precisa ser a `assets` exata; a escolha fica salva.

Lista com busca (por nome, alias ou número) e filtro por categoria, preview animado, addons, cores da paleta do jogo
ou hex livre, montaria, direção, velocidade, botão "Aleatório" e exportação em GIF/PNG.

## Nomes e categorias

O cliente só sabe *desenhar* cada looktype; ele não diz o que é outfit de player, montaria ou criatura, nem os nomes.
Isso fica em `core/src/main/resources/org/tibiawalk/core/metadata.json`, gerado a partir de:

- `data/XML/outfits.xml` e `data/XML/mounts.xml` do [Canary](https://github.com/opentibiabr/canary) (outfits e montarias);
- `staticdata-*.dat` do cliente (monstros e bosses do Cyclopedia).

Para atualizar (por exemplo, depois de uma atualização do Tibia):

```bash
./gradlew :core:updateMetadata -Passets="C:/.../Tibia/packages/Tibia/assets"
```

Looktypes que ainda não estão no Canary continuam renderizando normalmente, só aparecem sem nome (`list --unknown`).

## Módulos

- `core`: leitura do `catalog-content.json`, `appearances.dat` (protobuf) e das folhas `sprites-*.bmp.lzma`; renderização e GIF.
- `cli`: linha de comando.
- `desktop`: interface Swing.
