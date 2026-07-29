#!/usr/bin/env sh
set -eu

repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
acceptance_root=$(mktemp -d "${TMPDIR:-/tmp}/backsmith-acceptance.XXXXXX")
jar="$repository/backsmith-cli/target/backsmith.jar"

cd "$repository"
chmod +x mvnw
./mvnw -B -ntp clean verify

for architecture in layered hexagonal modular-monolith clean onion cqrs microservice; do
  name="sample-$architecture"
  java -jar "$jar" create "$name" --project "$acceptance_root" --architecture "$architecture" --yes --quiet
  project="$acceptance_root/$name"
  chmod +x "$project/mvnw"
  (
    cd "$project"
    ./mvnw -B -ntp verify
    if command -v docker >/dev/null 2>&1; then docker compose config --quiet; fi
  )
done

hexagonal="$acceptance_root/sample-hexagonal"
java -jar "$jar" module payment --project "$hexagonal" --quiet
java -jar "$jar" entity Payment --project "$hexagonal" --module payment \
  --field "id:uuid:required" \
  --field "amount:decimal:required:precision=19:scale=4" \
  --field "currency:string:required:length=3" \
  --field "status:enum[CREATED,PROCESSING,COMPLETED,FAILED]:required" \
  --quiet
java -jar "$jar" api "$repository/examples/contracts/payments.yaml" \
  --project "$hexagonal" --module payment --quiet
(
  cd "$hexagonal"
  ./mvnw -B -ntp verify
)

java -jar "$jar" entity Customer --project "$hexagonal" --module customer --dry-run --quiet
test ! -e "$hexagonal/src/main/java/com/example/samplehexagonal/modules/customer"

owned="$hexagonal/src/main/java/com/example/samplehexagonal/modules/payment/domain/model/Payment.java"
printf '\n// user change\n' >> "$owned"
set +e
java -jar "$jar" entity Payment --project "$hexagonal" --module payment \
  --field "id:uuid:required" \
  --field "amount:decimal:required:precision=19:scale=4" \
  --field "currency:string:required:length=3" \
  --field "status:enum[CREATED,PROCESSING,COMPLETED,FAILED]:required" \
  --quiet
conflict_exit=$?
set -e
test "$conflict_exit" -eq 4

echo "Backsmith acceptance passed. Generated projects: $acceptance_root"
