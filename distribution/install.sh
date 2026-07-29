#!/usr/bin/env sh
set -eu

REPOSITORY="SanketKudale/backsmith"
INSTALL_ROOT="${BACKSMITH_HOME:-$HOME/.local/share/backsmith}"
BIN_DIR="${BACKSMITH_BIN_DIR:-$HOME/.local/bin}"
BASE_URL="https://github.com/$REPOSITORY/releases/latest/download"
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT INT TERM

command -v java >/dev/null 2>&1 || {
  echo "Backsmith requires Java 21 or newer." >&2
  exit 1
}

JAVA_VERSION=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 21 ]; then
  echo "Backsmith requires Java 21 or newer; detected Java ${JAVA_VERSION:-unknown}." >&2
  exit 1
fi

curl -fsSL "$BASE_URL/backsmith.tar.gz" -o "$TEMP_DIR/backsmith.tar.gz"
curl -fsSL "$BASE_URL/SHA256SUMS" -o "$TEMP_DIR/SHA256SUMS"

EXPECTED=$(awk '$2 == "backsmith.tar.gz" { print $1 }' "$TEMP_DIR/SHA256SUMS")
if [ -z "$EXPECTED" ]; then
  echo "Release checksum for backsmith.tar.gz was not found." >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$TEMP_DIR/backsmith.tar.gz" | awk '{ print $1 }')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$TEMP_DIR/backsmith.tar.gz" | awk '{ print $1 }')
else
  echo "A SHA-256 tool (sha256sum or shasum) is required." >&2
  exit 1
fi

if [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "Checksum verification failed; installation stopped." >&2
  exit 1
fi

mkdir -p "$TEMP_DIR/extracted" "$INSTALL_ROOT" "$BIN_DIR"
tar -xzf "$TEMP_DIR/backsmith.tar.gz" -C "$TEMP_DIR/extracted"
ARCHIVE_ROOT=$(find "$TEMP_DIR/extracted" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [ -z "$ARCHIVE_ROOT" ]; then
  echo "Release archive did not contain the expected directory." >&2
  exit 1
fi

cp -R "$ARCHIVE_ROOT/." "$INSTALL_ROOT/"
ln -sf "$INSTALL_ROOT/bin/backsmith" "$BIN_DIR/backsmith"

echo "Backsmith installed to $INSTALL_ROOT"
echo "Ensure $BIN_DIR is on PATH, then run: backsmith --help"
