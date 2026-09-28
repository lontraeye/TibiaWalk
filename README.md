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

## Web

```bash
./gradlew :web:fatJar                       # gera web/build/libs/tibiawalk-web.jar
java -jar web/build/libs/tibiawalk-web.jar --assets "C:/.../Tibia/packages/Tibia/assets" --port 7070
```

Abra `http://localhost:7070/` para o outfitter (visual inspirado nas janelas do Tibia). O estado fica na URL,
então dá para compartilhar o link de um outfit montado.

### Colocar num site

```html
<script src="http://SEU-SERVIDOR:7070/tibiawalk.js"></script>
<tibia-outfit looktype="Citizen" female addons="3" head="78" body="69" legs="58" feet="76"
              mount="Widow Queen" direction="west" scale="2"></tibia-outfit>
```

Atributos: `looktype` (número ou nome), `female`, `addons`, `head`/`body`/`legs`/`feet` (0–132 ou hex), `mount`
(número ou nome), `direction`, `idle`, `frame-ms`, `format` (`gif`/`png`), `scale` e `server`.
Em caso de erro o elemento dispara o evento `tibiawalk-error` com a mensagem em `event.detail`.

### API

| Rota | O quê |
|---|---|
| `GET /api/outfit.gif?looktype=…` | GIF animado (mesmos parâmetros do componente; `frameMs` em vez de `frame-ms`) |
| `GET /api/outfit.png?looktype=…` | PNG parado |
| `GET /api/looktypes?kind=player\|mount\|creature\|unknown&sex=male\|female&q=…` | lista com nome e capacidades |
| `GET /api/mounts`, `GET /api/palette`, `GET /api/info` | montarias, as 133 cores, versão dos dados |

As imagens saem com `Cache-Control` de 1 dia e ficam em cache no servidor; CORS liberado para qualquer site.

## Nomes e categorias

O cliente só sabe *desenhar* cada looktype; ele não diz o que é outfit de player, montaria ou criatura, nem os nomes.
Isso fica em `core/src/main/resources/org/tibiawalk/core/metadata.json`, gerado juntando, nesta ordem:

1. [TibiaWiki](https://tibia.fandom.com) (conteúdo CC BY-SA): outfits (`male_id`/`female_id`) e montarias, o mais atualizado;
2. `outfits.xml` e `mounts.xml` do [Canary](https://github.com/opentibiabr/canary): completa o que faltar e dá o id interno
   da montaria. Se as fontes discordarem no sexo, vale o Canary (conferido nos sprites), a não ser que ele mesmo dê o
   mesmo sexo aos dois lados do par;
3. `staticdata-*.dat` do cliente: monstros e bosses do Cyclopedia;
4. heurística: looktype sem nome que tem versão montada e cores é outfit de player (`Outfit #1640/1641`); pares
   consecutivos viram masculino/feminino (menor = masculino, provisório);
5. [`core/metadata-overrides.json`](core/metadata-overrides.json): correções manuais, com prioridade sobre tudo.

Cada entrada guarda a fonte em `source`. Para nomear um outfit novo ou corrigir um sexo, edite o overrides:

```json
[
  {"looktype": 1640, "name": "Nome do outfit", "sex": "male"},
  {"looktype": 1641, "name": "Nome do outfit", "sex": "female"}
]
```

Para atualizar (por exemplo, depois de uma atualização do Tibia):

```bash
./gradlew :core:updateMetadata -Passets="C:/.../Tibia/packages/Tibia/assets"
```

Looktypes que ainda não estão no Canary continuam renderizando normalmente, só aparecem sem nome (`list --unknown`).

## Módulos

- `core`: leitura do `catalog-content.json`, `appearances.dat` (protobuf) e das folhas `sprites-*.bmp.lzma`; renderização e GIF.
- `cli`: linha de comando.
- `desktop`: interface Swing.
- `web`: servidor HTTP (Javalin), página de outfitter e o componente `<tibia-outfit>`.
