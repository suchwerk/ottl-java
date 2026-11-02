#!/usr/bin/env bash
#
# Builds the Vue/Quasar editor and copies the static bundle into the Quarkus example so it is
# served directly by the running application. No Node.js is required at runtime afterwards.
#
# Usage: examples/ottl-editor/scripts/build-static.sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
frontend_dir="$(cd "${here}/.." && pwd)"
target_dir="$(cd "${frontend_dir}/../quarkus-example/src/main/resources/META-INF/resources" 2>/dev/null \
  && pwd || echo "${frontend_dir}/../quarkus-example/src/main/resources/META-INF/resources")"

echo ">> Building front end in ${frontend_dir}"
(cd "${frontend_dir}" && npm run build)

echo ">> Publishing static bundle to ${target_dir}"
mkdir -p "${target_dir}"
# Remove a previous copy of the editor assets only (leave other static resources intact).
rm -rf "${target_dir}/assets" "${target_dir}/index.html"
cp -R "${frontend_dir}/dist/." "${target_dir}/"

echo ">> Done. Start Quarkus (./mvnw -pl examples/quarkus-example quarkus:dev) and open http://localhost:8080/"
