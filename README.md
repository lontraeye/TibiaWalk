# TibiaWalk

Outfitter de Tibia: monta qualquer outfit, com addons, cores, montaria e direção, e gera o **GIF andando** ou o
PNG, lendo os sprites direto do **cliente oficial**. Também tem NPCs, monstros e bosses já vestidos com a roupa
deles.

**No ar:** https://outfits.lontraeye.com

![stalker](tibia-stalker.gif)
![magex](tibia-ezgif.com-gif-maker.gif)

São quatro jeitos de usar o mesmo motor:

| | Para quê |
|---|---|
| **Web** | Outfitter no navegador, com visual inspirado nas janelas do Tibia, link compartilhável e API de imagens |
| **`<tibia-outfit>`** | Componente para colar em qualquer site (guild, fansite, blog) |
| **Desktop** | App Swing para usar offline |
| **CLI** | Linha de comando, para gerar imagens em lote ou por script |

---

## Sumário

- [Usar no seu site](#usar-no-seu-site)
- [Rodar na sua máquina](#rodar-na-sua-máquina)
  - [Web](#web) · [Desktop](#desktop) · [CLI](#cli)
- [API](#api)
- [Como funciona](#como-funciona)
- [Nomes e categorias](#nomes-e-categorias)
- [Manutenção](#manutenção): patch do Tibia e atualização do site
- [Estrutura do projeto](#estrutura-do-projeto)
- [Aviso](#aviso)

---

## Usar no seu site

Não precisa instalar nada. Cole isto no HTML:

```html
<script src="https://outfits.lontraeye.com/tibiawalk.js"></script>

<tibia-outfit looktype="Citizen" female addons="3" head="78" body="69" legs="58" feet="76"
              mount="Widow Queen" direction="west" scale="2"></tibia-outfit>

<tibia-outfit npc="Rashid" scale="2"></tibia-outfit>
<tibia-outfit monster="Black Knight" scale="2"></tibia-outfit>
```

O jeito mais fácil é montar o outfit em https://outfits.lontraeye.com e copiar o código da seção
**"Usar no seu site"**.

| Atributo | Valores |
|---|---|
| `looktype` | número (`128`) ou nome (`Citizen`, `"orc warlord"`) |
| `npc` / `monster` | nome do personagem, já com as cores, addons e montaria dele (monstro inclui bosses) |
| `female` | usa a versão feminina (com nome de outfit de player) |
| `addons` | `0` nenhum, `1` primeiro, `2` segundo, `3` ambos |
| `head` `body` `legs` `feet` | índice da paleta do jogo (`0`–`132`) ou hex (`ff0000`) |
| `mount` | looktype ou nome da montaria |
| `direction` | `north`, `east`, `south` (padrão), `west` |
| `idle` | parado em vez de andando |
| `frame-ms` | duração de cada frame (padrão 100) |
| `format` | `gif` (padrão) ou `png` |
| `scale` | ampliação na tela, sem borrar (`2`, `3`…) |
| `server` | outro servidor TibiaWalk (padrão: de onde veio o script) |

Os atributos explícitos sobrescrevem o preset do personagem: `<tibia-outfit monster="Black Knight" head="0">`.
Em caso de erro, o elemento dispara o evento `tibiawalk-error` com a mensagem em `event.detail`.

---

## Rodar na sua máquina

**Requisitos:** Java 21 e o **cliente Tibia** instalado e atualizado. O TibiaWalk usa a pasta `assets` do
cliente, normalmente em `%LOCALAPPDATA%\Tibia\packages\Tibia\assets` ou dentro da pasta de instalação do launcher.
Os sprites são da CipSoft e **não** fazem parte deste repositório.

Para não repetir o caminho em todo comando, defina a variável `TIBIA_ASSETS`:

```bash
export TIBIA_ASSETS="C:/Users/voce/AppData/Local/Tibia/packages/Tibia/assets"   # Git Bash
$env:TIBIA_ASSETS="C:/Users/voce/AppData/Local/Tibia/packages/Tibia/assets"     # PowerShell
```

### Web

```bash
./gradlew :web:fatJar
java -jar web/build/libs/tibiawalk-web.jar          # --assets <pasta> --port 7070 --host 0.0.0.0
```

Abra http://localhost:7070/. O que dá para fazer na página:

- outfit, sexo, addons, cores (paleta ou hex), montaria, direção e velocidade
- coleção com abas **Outfits, Montarias, NPCs, Monstros** (com filtro "só bosses"), **Sem nome** e **Todos**,
  com miniaturas e busca
- em NPCs e monstros, o botão **"Ir para o outfit"** leva ao outfit de player que eles vestem
- baixar GIF/PNG, copiar o código de incorporação e o link
- o estado fica na URL, então o link reabre exatamente o mesmo outfit

### Desktop

```bash
./gradlew :desktop:fatJar
java -jar desktop/build/libs/tibiawalk-desktop.jar  # ou duplo clique no jar
```

Na primeira vez ele procura a pasta do cliente (`TIBIA_ASSETS`, a pasta padrão do Windows ou a última usada) e,
se não achar, pergunta. Pode apontar para a pasta de instalação do launcher, não precisa ser a `assets` exata.
Tem os mesmos recursos da página, fora o link compartilhável e o código de incorporação, que só fazem sentido
no web.

### CLI

```bash
./gradlew :cli:fatJar
alias tibiawalk="java -jar cli/build/libs/tibiawalk.jar"

tibiawalk render -l Citizen --female -a 3 --head 78 --body 69 --legs 58 --feet 76 -m "Widow Queen" -d west
tibiawalk render -l 130 -a 3 --head ff0000 --body 00a9ff --legs 0055ff --feet 557f00 -o mago.gif
tibiawalk render -l 128 -a 1 --idle -o citizen.png
tibiawalk render --npc "A Bearded Woman"
tibiawalk render --monster "Black Knight" --head 0          # preset + ajuste

tibiawalk info "Midnight Panther"
tibiawalk list --players --female --with-addons
tibiawalk list --mounts --json
tibiawalk list --npcs -s rashid
tibiawalk list --monsters -s ferumbras
tibiawalk list --unknown                                    # looktypes sem nome conhecido
```

Sem `-o`, a saída vai para `generated/`, com um nome que descreve o outfit. `tibiawalk <comando> --help` mostra
todas as opções.

---

## API

O servidor web expõe:

| Rota | O quê |
|---|---|
| `GET /api/outfit.gif?looktype=…` | GIF animado |
| `GET /api/outfit.png?looktype=…` | PNG parado |
| `GET /api/looktypes?kind=player\|mount\|creature\|npc\|unknown\|all&sex=male\|female&q=…` | looktypes com nome e o que suportam |
| `GET /api/characters?kind=npc\|monster&q=…` | NPCs ou monstros/bosses com o outfit completo |
| `GET /api/mounts` · `GET /api/palette` · `GET /api/info` | montarias, as 133 cores, contagens e versão dos dados |

As imagens aceitam os mesmos parâmetros do componente (`frameMs` em vez de `frame-ms`), e `npc=`/`monster=`
no lugar de `looktype=`. Exemplo:
https://outfits.lontraeye.com/api/outfit.gif?looktype=Citizen&female&addons=3&mount=Widow%20Queen&direction=west

As imagens saem com `Cache-Control` de 1 dia (1 hora quando a URL usa nomes) e ficam em cache no servidor. CORS
é liberado para qualquer site, e erros voltam em JSON (`{"error": "..."}`) com status 400.

---

## Como funciona

```
 cliente Tibia (pasta assets)                 metadata.json (embutido no jar)
 ├─ catalog-content.json  índice              ├─ looktypes: nome, categoria, sexo
 ├─ appearances-*.dat     protobuf            └─ characters: NPCs/monstros com outfit
 ├─ staticdata-*.dat      bestiário                     ▲
 └─ sprites-*.bmp.lzma    folhas 384×384                │ ./gradlew updateAll
            │                                           │ (TibiaWiki + Canary + staticdata)
            ▼                                           │
       ┌──────────────────── core ───────────────────────┐
       │ AssetCatalog → SpriteStore (decodifica e cacheia │
       │ as folhas) → OutfitRenderer (compõe e colore) →  │
       │ GifWriter                                        │
       └────────┬──────────────┬───────────────┬─────────┘
               cli          desktop           web (Javalin + página + tibiawalk.js)
```

- **`appearances.dat`:** para cada looktype, as direções, as camadas de addon, a versão montada, o template de cor
  e os frames de cada animação, com os IDs dos sprites.
- **Folhas de sprite:** `.bmp.lzma` com um cabeçalho da CipSoft, fluxo LZMA e BMP de 32 bits dentro.
- **Cores:** a camada de *template* marca cabeça (amarelo), corpo (vermelho), pernas (verde) e pés (azul). A cor
  escolhida multiplica o tom do sprite, com a mesma paleta HSI de 133 cores do jogo.
- **Ordem das camadas:** montaria, corpo, addon 1, addon 2. A flag `reverse_addons_<direção>` inverte a ordem dos
  addons naquela direção. Montado, o cavaleiro usa o deslocamento (`shift`) da montaria.

---

## Nomes e categorias

O cliente só sabe **desenhar** cada looktype. Ele não diz o que é outfit de player, montaria ou criatura, nem os
nomes. Isso fica no [`metadata.json`](core/src/main/resources/org/tibiawalk/core/metadata.json), gerado juntando
estas fontes em ordem de prioridade:

1. **[TibiaWiki](https://tibia.fandom.com)** (CC BY-SA): outfits (`male_id`/`female_id`) e montarias, a fonte
   mais atualizada.
2. **[Canary](https://github.com/opentibiabr/canary)** `outfits.xml`/`mounts.xml`: completa o que faltar. Se as
   fontes discordarem no sexo, vale o Canary (conferido nos sprites), a não ser que ele mesmo dê o mesmo sexo
   aos dois lados do par.
3. **`staticdata` do cliente:** monstros e bosses do bestiário, com cores e addons.
4. **Arquivos Lua do Canary** (`*/monster/**.lua`, `*/npc/**.lua`): NPCs e criaturas fora do bestiário.
5. **Heurística:** um looktype sem nome que tem versão montada e cores é outfit de player (`Outfit #1640/1641`).
6. **[`core/metadata-overrides.json`](core/metadata-overrides.json):** correções manuais, com prioridade sobre tudo.

Cada entrada guarda a fonte em `source`. Para nomear um outfit novo ou corrigir um sexo:

```json
[
  {"looktype": 1640, "name": "Nome do outfit", "sex": "male"},
  {"looktype": 1641, "name": "Nome do outfit", "sex": "female"}
]
```

Looktypes que nenhuma fonte conhece continuam renderizando normalmente, só aparecem sem nome.

---

## Manutenção

### Depois de um patch do Tibia

1. Abra o launcher e deixe ele atualizar. Os sprites são lidos direto da pasta `assets`, então outfits novos já
   renderizam.
2. Na raiz do projeto: `./gradlew updateAll`. Esse comando regenera o `metadata.json` e os três jars e mostra um
   relatório: conflitos de sexo, outfits provisórios e **o que mudou** (ganhou nome, mudou, perdeu nome).
3. Revise, ajuste o `core/metadata-overrides.json` se precisar e commite o `metadata.json`.

### Atualizar o site

O site roda numa VM Oracle Cloud (Always Free) atrás do Caddy. Com a chave em `~/.ssh/oracle_tibiawalk`:

```bash
deploy/update.sh             # código novo (inclui o metadata.json)
deploy/update.sh --assets    # depois de um patch do Tibia: também envia os assets do cliente
```

O script gera o pacote, envia, reinstala o serviço e confere se o site voltou (cerca de 25 s sem os assets).
Detalhes, montagem do zero e comandos da VM estão em [`deploy/README.md`](deploy/README.md).

---

## Estrutura do projeto

| Módulo | Conteúdo |
|---|---|
| `core` | leitura dos assets (`assets/`), decodificação das folhas (`sprites/`), renderização e GIF (`render/`), nomes e categorias (`metadata/`), ferramentas de depuração (`tools/`) |
| `cli` | comandos `render`, `list`, `info` (picocli) |
| `desktop` | interface Swing |
| `web` | servidor Javalin, página (`resources/public`) e componente `tibiawalk.js` |
| `deploy` | instalação na VM (`install.sh`, serviço systemd) e atualização (`update.sh`) |

Tasks úteis:

| Comando | O quê |
|---|---|
| `./gradlew updateAll` | metadata + os três jars |
| `./gradlew :core:updateMetadata` | só o metadata |
| `./gradlew :web:deployBundle [-Passets=…]` | pacote de deploy em `generated/deploy` |
| `./gradlew :core:outfitReport -Pids=128,130` | resumo do que o cliente tem |
| `./gradlew :core:tool -Ptool=AddonOrderCheck -Pargs="<assets> 1940 2 saida.png"` | compara as ordens de camada de um looktype |
| `./gradlew :core:tool -Ptool=DecodeBenchmark -Pargs="<assets> 80"` | tempo de decodificação das folhas |
| `./gradlew :desktop:snapshot -Pactions="…"` | abre o app e tira prints automatizados |

---

## Aviso

Tibia e os sprites são propriedade da **CipSoft GmbH**. Este é um projeto de fã, sem afiliação: os sprites são
lidos do cliente oficial instalado na máquina e não são distribuídos neste repositório. Nomes e IDs vêm do
TibiaWiki (CC BY-SA) e do Canary.
