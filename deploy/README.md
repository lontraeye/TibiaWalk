# Deploy: Oracle Cloud (Always Free) + Caddy

No ar em **https://outfits.lontraeye.com**, numa VM `VM.Standard.E2.1.Micro` (Ubuntu 24.04, x86, 1 GB).
O TibiaWalk escuta só em `127.0.0.1:7070`; o Caddy, na mesma VM, publica nas portas 80/443 e cuida do
certificado HTTPS (Let's Encrypt). O DNS do domínio está no Cloudflare.

```
navegador ──HTTPS──> Caddy :443 (VM Oracle) ──> 127.0.0.1:7070 (serviço tibiawalk)
```

| Onde | O quê |
|---|---|
| `/opt/tibiawalk/` | jar e `assets/` do cliente |
| `/etc/tibiawalk.env` | porta, host, memória (`JAVA_OPTS`) |
| `/etc/systemd/system/tibiawalk.service` | serviço |
| `/etc/caddy/Caddyfile` | `outfits.lontraeye.com { reverse_proxy 127.0.0.1:7070 }` |
| `~/tibiawalk/` (usuário `ubuntu`) | último pacote enviado |

A micro tem pouca CPU: cada imagem nova leva ~1 s para gerar na primeira vez; depois sai do cache do servidor.
Ligar o proxy do Cloudflare no registro DNS (nuvem laranja, SSL **Full (strict)**) põe as imagens também no cache
da borda.

Os passos abaixo são para montar do zero (ou numa VM nova).

## 0. Reconhecer a VM (VM já usada antes)

Na VM (via SSH ou pelo Cloud Shell do console da Oracle), rode e guarde a saída:

```bash
grep -E '^(NAME|VERSION)=' /etc/os-release; uname -m; nproc; free -h; df -h /
sudo ss -tlnp
systemctl list-units --type=service --state=running --no-pager
docker ps -a 2>/dev/null; pm2 list 2>/dev/null
command -v cloudflared nginx caddy java
```

Isso mostra o sistema, a arquitetura (ARM `aarch64` ou `x86_64`), a memória e **como o projeto antigo roda**
(serviço, Docker, pm2...), para removê-lo sem deixar sobra.

## 1. Remover o projeto antigo

Depende do que apareceu no passo 0. Em geral é um destes:

```bash
# serviço systemd
sudo systemctl disable --now NOME.service && sudo rm /etc/systemd/system/NOME.service && sudo systemctl daemon-reload
# Docker
docker rm -f CONTAINER          # e, se não quiser mais a imagem: docker rmi IMAGEM
# pm2
pm2 delete NOME && pm2 save
```

Se ele usava Caddy, nginx ou `cloudflared` para publicar, reaproveite no passo 4 trocando o destino para
`127.0.0.1:7070`.

## 2. Montar e enviar o pacote

No seu PC, na raiz do projeto:

```bash
./gradlew :web:deployBundle -Passets="C:/Users/lucas.cardozo/Documents/Projects/Tibia/packages/Tibia/assets"
scp -i CAMINHO/DA/CHAVE generated/deploy/* USUARIO@IP_DA_VM:~/tibiawalk/
```

O pacote tem o jar (~9 MB), os assets do cliente (~120 MB), o `install.sh` e o `tibiawalk.service`.
`USUARIO` costuma ser `ubuntu` (imagem Ubuntu) ou `opc` (Oracle Linux). Crie a pasta antes, se precisar:
`ssh -i CHAVE USUARIO@IP mkdir -p tibiawalk`.

> Se a rede bloquear SSH (ex.: faculdade), use o **Cloud Shell** do console da Oracle, que funciona pelo
> navegador: ele tem "Upload" no menu e, de lá, `scp` para a VM pelo IP privado.

## 3. Instalar

Na VM:

```bash
cd ~/tibiawalk
sudo bash install.sh tibiawalk-web.jar tibia-assets.tar.gz
```

O script instala o Java 21 se faltar, cria o usuário `tibiawalk`, põe tudo em `/opt/tibiawalk`, cria
`/etc/tibiawalk.env` e sobe o serviço. Termina com `==> OK: TibiaWalk respondendo em http://127.0.0.1:7070/`.

Teste na própria VM: `curl -s http://127.0.0.1:7070/api/info`.

## 4. Publicar

### Com Caddy (o que está em uso)

Requer as portas 80 e 443 liberadas na VM (iptables e *Security List* da Oracle).

```bash
sudo apt-get install -y caddy          # se ainda não tiver
sudo tee /etc/caddy/Caddyfile >/dev/null <<'EOF'
outfits.lontraeye.com {
    encode gzip
    reverse_proxy 127.0.0.1:7070
}
EOF
sudo systemctl reload caddy
```

No Cloudflare, **DNS → Add record**: tipo `A`, nome `outfits`, IP público da VM, proxy **DNS only** (para o
Caddy conseguir o certificado). Com o certificado emitido, dá para ligar o proxy com SSL em Full (strict).

### Alternativa: Cloudflare Tunnel (sem abrir portas)

No painel do Cloudflare: **Zero Trust → Networks → Tunnels → Create a tunnel**.

1. Tipo **Cloudflared**, nome `tibiawalk`.
2. Escolha o sistema da VM (Debian para Ubuntu, Red Hat para Oracle Linux) e a arquitetura (`arm64` se o
   passo 0 mostrou `aarch64`). O painel mostra um comando com um token: **rode-o na VM**. Ele instala o
   `cloudflared` como serviço.
3. Em **Public hostname**: subdomínio `outfits`, domínio `lontraeye.com`, tipo `HTTP`, URL `localhost:7070`.

O DNS é criado sozinho.

As imagens (`/api/outfit.gif|png`) saem com `Cache-Control`; o Cloudflare guarda em cache pela extensão,
então o servidor só gera cada GIF uma vez por dia (ou por hora, se a URL usar nomes).

## Atualizar depois

- **Código novo** (só o jar): `./gradlew :web:deployBundle`, envie `tibiawalk-web.jar`, e na VM
  `sudo bash install.sh tibiawalk-web.jar`.
- **Patch do Tibia**: atualize o cliente no PC, rode `./gradlew updateAll` (nomes novos) e
  `./gradlew :web:deployBundle -Passets=...`, envie o jar e o `tibia-assets.tar.gz`, e na VM
  `sudo bash install.sh tibiawalk-web.jar tibia-assets.tar.gz`.

## Comandos úteis na VM

```bash
sudo systemctl status tibiawalk        # está rodando?
sudo journalctl -u tibiawalk -f        # log ao vivo
sudo systemctl restart tibiawalk
sudo nano /etc/tibiawalk.env           # porta, memória (JAVA_OPTS); depois restart
```
