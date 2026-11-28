#!/usr/bin/env bash
# Firma un APK con la clave de release y el linaje de rotación (clave de depuración → clave de release),
# para que las instalaciones antiguas acepten la actualización sin desinstalar.
#
#   scripts/sign-apk.sh entrada.apk salida.apk
#
# Variables: KEYSTORE (ruta del .jks), KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
# En local, si no están, se leen de ../material-island-keys/keystore.properties.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
in="$1"; out="$2"
props="$here/../material-island-keys/keystore.properties"
if [[ -z "${KEYSTORE:-}" && -f "$props" ]]; then
  KEYSTORE=$(grep '^storeFile=' "$props" | cut -d= -f2-)
  KEYSTORE_PASSWORD=$(grep '^storePassword=' "$props" | cut -d= -f2-)
  KEY_ALIAS=$(grep '^keyAlias=' "$props" | cut -d= -f2-)
  KEY_PASSWORD=$(grep '^keyPassword=' "$props" | cut -d= -f2-)
fi
export KEYSTORE_PASSWORD KEY_PASSWORD
: "${KEYSTORE:?no keystore}" "${KEYSTORE_PASSWORD:?}" "${KEY_ALIAS:?}" "${KEY_PASSWORD:?}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
bt=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)
"$bt/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
  --lineage "$here/signing/lineage.bin" \
  --min-sdk-version 31 --v1-signing-enabled false --v2-signing-enabled false --rotation-min-sdk-version 28 \
  --out "$out" "$in"
"$bt/apksigner" verify --min-sdk-version 31 "$out"
echo "Signed: $out"
