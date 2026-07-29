#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MVN_HOME="$ROOT/.mvn/apache-maven-3.9.11"
if [ ! -x "$MVN_HOME/bin/mvn" ]; then
  echo "Downloading Apache Maven 3.9.11..."
  ARCHIVE="${TMPDIR:-/tmp}/backsmith-maven-3.9.11.tar.gz"
  curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz" -o "$ARCHIVE"
  tar -xzf "$ARCHIVE" -C "$ROOT/.mvn"
  rm -f "$ARCHIVE"
fi
exec "$MVN_HOME/bin/mvn" "$@"
