#!/usr/bin/env bash
# One-time setup: creates the key that signs the catalog APK and stores it in GitHub secrets.
# Updates (Obtainium, adb install -r) only work while every APK is signed with this same key,
# so BACK UP the keystore file this script prints the path of. Nothing secret is printed.
# Needs: keytool (any JDK), openssl, gh (logged in, admin on the repo).
set -euo pipefail

REPO="${1:-KloosTech/Kompound}"
DIR="${KOMPOUND_KEYSTORE_DIR:-$HOME/.kompound}"
KEYSTORE="$DIR/catalog-release.keystore"
ALIAS="kompound-catalog"

if [ -e "$KEYSTORE" ]; then
  echo "Keystore already exists at $KEYSTORE; refusing to overwrite it." >&2
  exit 1
fi
mkdir -p "$DIR"; chmod 700 "$DIR"
PASSWORD="$(openssl rand -base64 24 | tr -d '/+=\n')"

keytool -genkeypair -v -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=Kompound Catalog, O=Kloos Tech" > /dev/null
chmod 600 "$KEYSTORE"

base64 < "$KEYSTORE" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set ANDROID_KEY_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set ANDROID_KEY_ALIAS --repo "$REPO"

# The password is only needed to re-create the secrets or sign locally; keep it next to the keystore.
umask 077; printf '%s\n' "$PASSWORD" > "$DIR/catalog-release.password"
echo "Done. Secrets set on $REPO."
echo "BACK UP: $KEYSTORE and $DIR/catalog-release.password (losing them means users must reinstall the app)."
