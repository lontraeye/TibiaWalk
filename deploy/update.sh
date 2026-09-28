#!/usr/bin/env bash
# Atualiza o site: gera o pacote, envia para a VM, reinstala e confere se voltou.
#
#   deploy/update.sh            # só o código (jar, com o metadata.json embutido)
#   deploy/update.sh --assets   # também os assets do cliente (depois de um patch do Tibia)
#
# Roda no Git Bash (Windows), Linux ou macOS, na raiz do projeto ou de qualquer lugar.
# Configuração por variável de ambiente (os padrões são os da VM atual):
#   TIBIAWALK_HOST  usuário@ip da VM         (ubuntu@147.15.58.105)
#   TIBIAWALK_KEY   chave SSH                (~/.ssh/oracle_tibiawalk)
#   TIBIAWALK_URL   endereço público do site (https://outfits.lontraeye.com)
#   TIBIA_ASSETS    pasta assets do cliente  (só para --assets)
set -euo pipefail

cd "$(dirname "$0")/.."
HOST="${TIBIAWALK_HOST:-ubuntu@147.15.58.105}"
KEY="${TIBIAWALK_KEY:-$HOME/.ssh/oracle_tibiawalk}"
URL="${TIBIAWALK_URL:-https://outfits.lontraeye.com}"
SSH=(ssh -i "$KEY" -o IdentitiesOnly=yes -o BatchMode=yes)
SCP=(scp -i "$KEY" -o IdentitiesOnly=yes -o BatchMode=yes -q)

WITH_ASSETS=false
if [ "${1:-}" = "--assets" ]; then
  WITH_ASSETS=true
  [ -n "${TIBIA_ASSETS:-}" ] || { echo "Defina TIBIA_ASSETS com a pasta assets do cliente." >&2; exit 1; }
fi
[ -f "$KEY" ] || { echo "Chave SSH não encontrada: $KEY (defina TIBIAWALK_KEY)" >&2; exit 1; }

echo "==> Gerando o pacote"
if $WITH_ASSETS; then
  ./gradlew :web:deployBundle -Passets="$TIBIA_ASSETS" --console=plain -q
else
  # Sem TIBIA_ASSETS o pacote leva só o jar e os scripts (compactar os assets demora e não mudou).
  env -u TIBIA_ASSETS ./gradlew :web:deployBundle --console=plain -q
fi

FILES=(generated/deploy/tibiawalk-web.jar generated/deploy/install.sh generated/deploy/tibiawalk.service)
ASSETS_ARG=""
if $WITH_ASSETS; then
  FILES+=(generated/deploy/tibia-assets.tar.gz)
  ASSETS_ARG="tibia-assets.tar.gz"
fi

echo "==> Enviando para $HOST"
"${SSH[@]}" "$HOST" "mkdir -p ~/tibiawalk"
"${SCP[@]}" "${FILES[@]}" "$HOST:tibiawalk/"

echo "==> Instalando"
"${SSH[@]}" "$HOST" "cd ~/tibiawalk && sudo bash install.sh tibiawalk-web.jar $ASSETS_ARG" | grep '==>'

echo "==> Conferindo $URL"
for _ in $(seq 1 20); do
  if curl -fs "$URL/api/info" >/dev/null; then
    echo "==> Site atualizado: $URL"
    exit 0
  fi
  sleep 2
done
echo "O site não respondeu. Log: ssh -i $KEY $HOST sudo journalctl -u tibiawalk -n 50" >&2
exit 1
