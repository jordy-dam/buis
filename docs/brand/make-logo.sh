#!/usr/bin/env bash
# Turns the round BUIS logo (buis-logo-source.png: a screenshot of the logo on a light background)
# into the logo, the launcher icon and the launcher banner. Needs ImageMagick. Re-run after
# replacing the source; the circle is found by its blue, so the source may be cropped differently.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BRAND="$ROOT/docs/brand"; RES="$ROOT/app/src/main/res"
SRC="$BRAND/buis-logo-source.png"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$RES/drawable-xhdpi" "$RES/drawable-xxxhdpi"

# 1. The circle: its blue sampled at the top centre, its bounding box from everything that blue.
BLUE="$(magick "$SRC" -format "%[pixel:p{w/2,h/10}]" info:)"
BOX="$(magick "$SRC" -fuzz 12% -fill black +opaque "$BLUE" -fill white -opaque "$BLUE" -format "%@" info:)"
SIDE="${BOX%%x*}"
magick "$SRC" -crop "$BOX" +repage "$TMP/square.png"
# Mask outside the circle instead of keying out the background: the logo's white is almost the
# background colour, so a colour key would eat the letters.
magick -size "${SIDE}x${SIDE}" xc:black -fill white -draw "circle $((SIDE/2)),$((SIDE/2)) $((SIDE/2)),1" "$TMP/mask.png"
magick "$TMP/square.png" "$TMP/mask.png" -alpha off -compose CopyOpacity -composite -resize 1024x1024 "$BRAND/buis-logo.png"

# 2. Launcher icon (108dp): the round logo, xhdpi and xxxhdpi.
magick "$BRAND/buis-logo.png" -resize 216x216 "$RES/drawable-xhdpi/ic_buis.png"
magick "$BRAND/buis-logo.png" -resize 432x432 "$RES/drawable-xxxhdpi/ic_buis.png"

# 3. Banner (320x180dp): the logo on its own blue. The circle is the same blue, so it disappears
#    and what's left is the "B" mark with "BUIS" under it, as in the logo, filling the height.
#    The outer 6% of the circle is cut off first: in the screenshot its edge blends with the light
#    background and would show as a thin line.
magick "$BRAND/buis-logo.png" \( -size 1024x1024 xc:black -fill white -draw "circle 512,512 512,32" \) \
  -alpha off -compose CopyOpacity -composite "$TMP/inner.png"
magick -size 1280x720 "xc:$BLUE" \( "$TMP/inner.png" -resize 820x820 \) -gravity center -geometry +0+6 -composite \
  "$RES/drawable-xxxhdpi/banner.png"
magick "$RES/drawable-xxxhdpi/banner.png" -resize 640x360 "$RES/drawable-xhdpi/banner.png"
cp "$RES/drawable-xxxhdpi/banner.png" "$BRAND/buis-banner.png"
echo "done: $BRAND/buis-logo.png, icon, banner"
