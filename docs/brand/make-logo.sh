#!/usr/bin/env bash
# Renders the BUIS wordmark (DIN Condensed Bold, plain, no effects) into the app's banner and icon.
# Needs ImageMagick and macOS (for the font). Re-run after changing size or colour.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FONT="/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf"
BG="#0E1114"; FG="#F2F4F5"
RES="$ROOT/app/src/main/res"
mkdir -p "$RES/drawable-xhdpi"
# Tv banner: 320x180 dp at xhdpi.
magick -size 640x360 "xc:$BG" -font "$FONT" -fill "$FG" -kerning 6 -pointsize 230 \
  -gravity center -annotate +0+36 "BUIS" "$RES/drawable-xhdpi/banner.png"
# App icon: 108x108 dp at xhdpi, rounded square.
magick -size 216x216 xc:none -fill "$BG" -draw "roundrectangle 0,0 215,215 48,48" \
  -font "$FONT" -fill "$FG" -kerning 2 -pointsize 92 -gravity center -annotate +0+16 "BUIS" "$RES/drawable-xhdpi/ic_buis.png"
cp "$RES/drawable-xhdpi/banner.png" "$ROOT/docs/brand/buis-banner.png"
echo "done"
