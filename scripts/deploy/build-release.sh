#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
architecture="${1:?Usage: build-release.sh arm64|amd64}"
case "$architecture" in arm64|amd64) ;; *) echo 'Unsupported architecture' >&2; exit 2 ;; esac
commit="$(git rev-parse HEAD)"
export SOURCE_DATE_EPOCH="$(git show -s --format=%ct HEAD)"
output="$root/.cache/release/$architecture"
mkdir -p "$output/infra/production" "$output/scripts/deploy"

locked() { node -e 'const fs=require("fs"); process.stdout.write(JSON.parse(fs.readFileSync("infra/production/runtime-lock.json"))[process.argv[1]])' "$1"; }
mapfile -t modules < <(node --input-type=module -e 'import {modules} from "./scripts/deploy/release.mjs"; console.log(modules.join("\n"))')
test -f backend/auth-service/src/main/resources/db/migration/V3__durable_browser_sessions.sql || {
  echo 'The secure browser-session release from #242 must be integrated first.' >&2; exit 1;
}
for module in "${modules[@]}"; do
  docker build --platform "linux/$architecture" --file infra/production/java/Dockerfile \
    --build-arg "MODULE=$module" --build-arg "JDK_IMAGE=$(locked jdk)" --build-arg "JRE_IMAGE=$(locked jre)" \
    --build-arg "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" \
    --label "org.opencontainers.image.revision=$commit" \
    --label 'org.opencontainers.image.source=https://github.com/Vinosaamaa/chanter' \
    --tag "chanter-$module:$commit" .
done
docker build --platform "linux/$architecture" --file infra/production/frontend/Dockerfile \
  --build-arg "CADDY_IMAGE=$(locked caddy)" --build-arg "SOURCE_DATE_EPOCH=$SOURCE_DATE_EPOCH" \
  --label "org.opencontainers.image.revision=$commit" --tag "chanter-frontend:$commit" .
for dependency in postgres redis livekit; do
  docker pull --platform "linux/$architecture" "$(locked "$dependency")"
  docker tag "$(locked "$dependency")" "chanter-$dependency:$commit"
done
names=("${modules[@]}" frontend postgres redis livekit)
for name in "${names[@]}"; do
  docker run --rm --volume /var/run/docker.sock:/var/run/docker.sock \
    --volume chanter-trivy-cache:/root/.cache/ "$(locked scanner)" image \
    --scanners vuln,secret --exit-code 1 --severity HIGH,CRITICAL "chanter-$name:$commit"
done
node scripts/deploy/create-manifest.mjs "$output/release.json" "$architecture"
mapfile -t images < <(node -e 'console.log(Object.values(require(process.argv[1]).images).join("\n"))' "$output/release.json")
docker save --output "$output/images.tar" "${images[@]}"
(cd "$output" && sha256sum images.tar > images.sha256)
cp scripts/deploy/{host.mjs,release.mjs} "$output/scripts/deploy/"
cp infra/production/{postgres-init.sh,livekit.yaml,runtime-lock.json,release-policy.json} "$output/infra/production/"
echo "Release bundle prepared for $commit ($architecture); no secrets or registry account required."
