#!/usr/bin/env bash
# Publish a non-snapshot release of ottl-java to Maven Central.
#
# The repository stays on a SNAPSHOT version. This script rewrites the reactor
# to the release version in the working tree, builds and deploys it, and does
# not commit. Run it from a clean checkout, or discard the version rewrite
# afterwards with git checkout -- '*.xml' '**/pom.xml' (or git restore).
#
# Required for a real upload:
#   CENTRAL_USERNAME   user-token username from central.sonatype.com
#   CENTRAL_PASSWORD   user-token password
#   GPG_PASSPHRASE     passphrase of a secret key already on the GPG agent
#
# The namespace de.suchwerk must already be verified on suchwerk.de.
#
# Usage:
#   ./scripts/publish-central.sh --version 0.1.0
#   ./scripts/publish-central.sh --version 0.1.0 --skip-upload
#
# --skip-upload builds the Central bundle and stops before the upload.
# The bundle is target/central-publishing/central-bundle.zip on the root module.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="${OTTL_RELEASE_VERSION:-}"
SKIP_UPLOAD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --skip-upload)
      SKIP_UPLOAD=true
      shift
      ;;
    -h|--help)
      sed -n '2,22p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$VERSION" && "${GITHUB_REF_TYPE:-}" == "tag" ]]; then
  VERSION="${GITHUB_REF_NAME#v}"
fi

if [[ -z "$VERSION" ]]; then
  echo "Pass --version 0.1.0, or set OTTL_RELEASE_VERSION." >&2
  exit 1
fi

VERSION="${VERSION#v}"

if [[ "$VERSION" == *SNAPSHOT* ]] || [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z]+)*$ ]]; then
  echo "Refusing to publish version '$VERSION'. Use a release version such as 0.1.0." >&2
  exit 1
fi

if [[ -z "${GPG_PASSPHRASE:-}" ]]; then
  echo "Set GPG_PASSPHRASE to the passphrase of the release signing key." >&2
  exit 1
fi

if ! gpg --batch --list-secret-keys --with-colons 2>/dev/null | grep -q '^sec:'; then
  echo "No GPG secret key is available. Import one before publishing." >&2
  exit 1
fi

if [[ "$SKIP_UPLOAD" == false ]]; then
  if [[ -z "${CENTRAL_USERNAME:-}" || -z "${CENTRAL_PASSWORD:-}" ]]; then
    echo "Set CENTRAL_USERNAME and CENTRAL_PASSWORD (Central Portal user token)." >&2
    exit 1
  fi
fi

CURRENT="$(python3 - <<'PY'
import xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse("pom.xml").getroot()
print(root.find("m:version", ns).text)
PY
)"

if [[ "$CURRENT" != "$VERSION" ]]; then
  echo "Setting reactor version $CURRENT -> $VERSION"
  ./mvnw -B -ntp org.codehaus.mojo:versions-maven-plugin:2.18.0:set \
    -DnewVersion="$VERSION" \
    -DgenerateBackupPoms=false \
    -DprocessAllModules=true
fi

SETTINGS="$(mktemp)"
cleanup() {
  rm -f "$SETTINGS"
}
trap cleanup EXIT

python3 - "$SETTINGS" "$SKIP_UPLOAD" <<'PY'
import html, os, sys
path, skip = sys.argv[1], sys.argv[2]
username = html.escape(os.environ.get("CENTRAL_USERNAME", ""))
password = html.escape(os.environ.get("CENTRAL_PASSWORD", ""))
servers = ""
if skip != "true":
    servers = f"""
    <servers>
      <server>
        <id>central</id>
        <username>{username}</username>
        <password>{password}</password>
      </server>
    </servers>"""
with open(path, "w", encoding="utf-8") as out:
    out.write(f"""<settings>{servers}
</settings>
""")
PY

DEPLOY_ARGS=(-B -ntp -s "$SETTINGS" deploy -P release)
if [[ "$SKIP_UPLOAD" == true ]]; then
  DEPLOY_ARGS+=("-DskipUpload=true")
  echo "Building the Central bundle for $VERSION without uploading."
else
  echo "Publishing $VERSION to Maven Central."
fi

./mvnw "${DEPLOY_ARGS[@]}"
