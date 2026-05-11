#!/usr/bin/env bash
set -euo pipefail

VERSION="$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/' | sed 's/-SNAPSHOT$//')"
OUTPUT_DIR="dist/linux"
INPUT_DIR="dist/_jpackage-input"
ICON_ARGS=()

if [[ -f "packaging/assets/pdf-stitchui.png" ]]; then
  ICON_ARGS=(--icon "packaging/assets/pdf-stitchui.png")
fi

rm -rf "$OUTPUT_DIR"
rm -rf "$INPUT_DIR"
mkdir -p "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR"
cp "target/pdf-stitchui-1.0.0-SNAPSHOT.jar" "$INPUT_DIR/"

jpackage \
  --type app-image \
  --name "PDF-StitchUI" \
  --app-version "$VERSION" \
  --vendor "AmesInc" \
  --input "$INPUT_DIR" \
  --dest "$OUTPUT_DIR" \
  --main-jar "pdf-stitchui-1.0.0-SNAPSHOT.jar" \
  --main-class "com.ameli.pdfstitcher.PdfStitcherApp" \
  "${ICON_ARGS[@]}"
