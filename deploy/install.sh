#!/usr/bin/env bash
# Instala ou atualiza o TibiaWalk web numa VM Linux (Ubuntu/Debian ou Oracle Linux/RHEL).
#
#   sudo ./install.sh tibiawalk-web.jar tibia-assets.tar.gz   # primeira vez (ou para trocar os assets)
#   sudo ./install.sh tibiawalk-web.jar                       # só atualizar o jar
#
# Resultado: serviço "tibiawalk" em http://127.0.0.1:7070 (só local; o Cloudflare Tunnel publica).
set -euo pipefail

JAR="${1:?Uso: sudo ./install.sh <tibiawalk-web.jar> [tibia-assets.tar.gz]}"
ASSETS_TAR="${2:-}"
APP_DIR=/opt/tibiawalk
ENV_FILE=/etc/tibiawalk.env
HERE="$(cd "$(dirname "$0")" && pwd)"

if [ "$(id -u)" -ne 0 ]; then
  echo "Rode com sudo." >&2
  exit 1
fi
[ -f "$JAR" ] || { echo "Jar não encontrado: $JAR" >&2; exit 1; }

echo "==> Java 21"
if ! java -version 2>&1 | grep -Eq 'version "(2[1-9]|[3-9][0-9])'; then
  if command -v apt-get >/dev/null; then
    apt-get update -q && apt-get install -y -q openjdk-21-jre-headless
  elif command -v dnf >/dev/null; then
    dnf install -y -q java-21-openjdk-headless
  else
    echo "Não sei instalar o Java aqui; instale o Java 21 e rode de novo." >&2
    exit 1
  fi
fi
java -version 2>&1 | head -1

echo "==> Usuário e pastas"
id tibiawalk >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /usr/sbin/nologin tibiawalk
install -d -o tibiawalk -g tibiawalk "$APP_DIR"
install -m 644 -o tibiawalk -g tibiawalk "$JAR" "$APP_DIR/tibiawalk-web.jar"

if [ -n "$ASSETS_TAR" ]; then
  echo "==> Assets do cliente"
  TMP="$(mktemp -d)"
  tar -xzf "$ASSETS_TAR" -C "$TMP"
  CATALOG="$(find "$TMP" -name catalog-content.json -print -quit)"
  [ -n "$CATALOG" ] || { echo "catalog-content.json não está no arquivo $ASSETS_TAR" >&2; rm -rf "${TMP:?}"; exit 1; }
  rm -rf "${APP_DIR:?}/assets.new"
  mv "$(dirname "$CATALOG")" "$APP_DIR/assets.new"
  rm -rf "${APP_DIR:?}/assets"
  mv "$APP_DIR/assets.new" "$APP_DIR/assets"
  chown -R tibiawalk:tibiawalk "$APP_DIR/assets"
  rm -rf "${TMP:?}"
fi
[ -f "$APP_DIR/assets/catalog-content.json" ] || {
  echo "Faltam os assets: rode de novo passando o tibia-assets.tar.gz." >&2
  exit 1
}
echo "   $(ls "$APP_DIR/assets" | wc -l) arquivos em $APP_DIR/assets"

echo "==> Configuração ($ENV_FILE)"
if [ ! -f "$ENV_FILE" ]; then
  # Heap pequeno cabe até na VM micro (1 GB); na ARM dá para subir.
  cat > "$ENV_FILE" <<EOF
TIBIA_ASSETS=$APP_DIR/assets
HOST=127.0.0.1
PORT=7070
JAVA_OPTS="-Xmx384m -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
EOF
  echo "   criado (edite para mudar porta/memória)"
else
  echo "   já existe, mantido"
fi

echo "==> Serviço"
install -m 644 "$HERE/tibiawalk.service" /etc/systemd/system/tibiawalk.service
systemctl daemon-reload
systemctl enable tibiawalk >/dev/null
systemctl restart tibiawalk

PORT="$(grep -E '^PORT=' "$ENV_FILE" | cut -d= -f2)"
for _ in $(seq 1 30); do
  if curl -fs "http://127.0.0.1:${PORT:-7070}/api/info" >/dev/null; then
    echo "==> OK: TibiaWalk respondendo em http://127.0.0.1:${PORT:-7070}/"
    exit 0
  fi
  sleep 1
done
echo "O serviço não respondeu em 30 s. Veja o log: sudo journalctl -u tibiawalk -n 50" >&2
exit 1
