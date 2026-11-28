#!/usr/bin/env bash
# Sube la clave de release a los secretos de GitHub Actions del repo (una sola vez,
# o de nuevo si cambias la contraseña). Lee ../material-island-keys/.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
keys="$here/../material-island-keys"
p="$keys/keystore.properties"
val() { grep "^$1=" "$p" | cut -d= -f2- | tr -d '\n'; }
cd "$here"
base64 -w0 "$keys/release.jks" | gh secret set KEYSTORE_BASE64
val storePassword | gh secret set KEYSTORE_PASSWORD
val keyAlias      | gh secret set KEY_ALIAS
val keyPassword   | gh secret set KEY_PASSWORD
gh secret list
