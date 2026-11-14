#!/usr/bin/env bash
# Turns the round BUIS logo (buis-logo-source.jpg: the ring logo on a flat dark square) into
# the logo, the launcher icon and the launcher banner. Needs ImageMagick. Re-run after replacing
# the source; if the ring moves, re-measure CX/CY/R (the outer edge of the metal ring, in pixels).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BRAND="$ROOT/docs/brand"; RES="$ROOT/app/src/main/res"
SRC="$BRAND/buis-logo-source.jpg"
CX=1000; CY=997; R=797
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$RES/drawable-xhdpi" "$RES/drawable-xxxhdpi"

# 1. The round logo: the ring and what's inside it, transparent outside so it sits on any screen.
SIDE=$((2 * R))
magick "$SRC" -crop "${SIDE}x${SIDE}+$((CX - R))+$((CY - R))" +repage "$TMP/square.png"
magick -size "${SIDE}x${SIDE}" xc:black -fill white -draw "circle $R,$R $R,2" -blur 0x1.5 "$TMP/mask.png"
magick "$TMP/square.png" "$TMP/mask.png" -alpha off -compose CopyOpacity -composite -resize 1024x1024 "$BRAND/buis-logo.png"

# 2. Launcher icon (108dp): the round logo, xhdpi and xxxhdpi.
magick "$BRAND/buis-logo.png" -resize 216x216 "$RES/drawable-xhdpi/ic_buis.png"
magick "$BRAND/buis-logo.png" -resize 432x432 "$RES/drawable-xxxhdpi/ic_buis.png"

# 3. Banner (320x180dp): the round logo filling the height, on the source's flat background colour
#    (picked from its top-left corner).
BG="$(magick "$SRC" -crop 1x1+20+20 -format '#%[hex:p{0,0}]' info:)"
magick -size 1280x720 "xc:$BG" \
  \( "$BRAND/buis-logo.png" -resize 690x690 \) -gravity center -composite "$RES/drawable-xxxhdpi/banner.png"
magick "$RES/drawable-xxxhdpi/banner.png" -resize 640x360 "$RES/drawable-xhdpi/banner.png"
cp "$RES/drawable-xxxhdpi/banner.png" "$BRAND/buis-banner.png"
echo "done: $BRAND/buis-logo.png, icon, banner"
