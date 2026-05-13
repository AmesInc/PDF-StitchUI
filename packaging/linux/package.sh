#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_ID="$(grep -m1 '<artifactId>' pom.xml | sed -E 's/.*<artifactId>([^<]+)<\/artifactId>.*/\1/')"
PROJECT_VERSION="$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')"
VERSION="$(printf '%s' "$PROJECT_VERSION" | sed 's/-SNAPSHOT$//')"
JAR_NAME="${ARTIFACT_ID}-${PROJECT_VERSION}.jar"
OUTPUT_DIR="dist/linux"
INPUT_DIR="dist/_jpackage-input"

rm -rf "$OUTPUT_DIR"
rm -rf "$INPUT_DIR"
mkdir -p "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "target/$JAR_NAME" "$INPUT_DIR/"
cp "LICENSE" "$INPUT_DIR/"
cp "THIRD_PARTY_NOTICES.md" "$INPUT_DIR/"

jpackage_args=(
  --type app-image
  --name "PDF-StitchUI"
  --app-version "$VERSION"
  --vendor "AmesInc"
  --input "$INPUT_DIR"
  --dest "$OUTPUT_DIR"
  --main-jar "$JAR_NAME"
  --main-class "com.ameli.pdfstitcher.PdfStitcherApp"
)

if [[ -f "packaging/assets/pdf-stitchui.png" ]]; then
  jpackage_args+=(--icon "packaging/assets/pdf-stitchui.png")
fi

jpackage "${jpackage_args[@]}"

cp "LICENSE" "$OUTPUT_DIR/"
cp "THIRD_PARTY_NOTICES.md" "$OUTPUT_DIR/"
