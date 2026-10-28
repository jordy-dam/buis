#!/usr/bin/env bash
# Generates local test media for the mock Xtream server (dev only, output is gitignored).
# Live: 4 looping HLS + MPEG-TS "channels" with different test patterns, AC3 audio on two of them
# so the FFmpeg audio decoder path gets exercised. VOD: one 3-minute MP4 (resume needs > 30s).
set -euo pipefail
OUT="$(cd "$(dirname "$0")" && pwd)/media"
mkdir -p "$OUT/live" "$OUT/img"

patterns=(testsrc2 smptebars rgbtestsrc pal75bars)
for i in 0 1 2 3; do
  dir="$OUT/live/$i"
  mkdir -p "$dir"
  if [ $((i % 2)) -eq 0 ]; then acodec="ac3"; else acodec="aac"; fi
  ffmpeg -loglevel error -y \
    -f lavfi -i "${patterns[$i]}=size=1280x720:rate=25" \
    -f lavfi -i "sine=frequency=$((440 + i * 110)):sample_rate=48000" \
    -t 60 -c:v libx264 -preset veryfast -g 25 -keyint_min 25 -sc_threshold 0 -b:v 1500k \
    -c:a "$acodec" -b:a 128k -ac 2 \
    "$dir/stream.ts"
  ffmpeg -loglevel error -y -i "$dir/stream.ts" -c copy \
    -f hls -hls_time 2 -hls_list_size 0 -hls_playlist_type vod \
    -hls_segment_filename "$dir/seg%03d.ts" "$dir/index.m3u8"
done

ffmpeg -loglevel error -y \
  -f lavfi -i "testsrc2=size=1280x720:rate=25" \
  -f lavfi -i "sine=frequency=330:sample_rate=48000" \
  -t 180 -c:v libx264 -preset veryfast -b:v 1200k -c:a ac3 -b:a 128k -ac 2 \
  -movflags +faststart "$OUT/movie.mp4"

# Placeholder artwork: posters (2:3), backdrops (16:9), channel logos (square).
colors=(0x1e88e5 0xe53935 0x43a047 0xfdd835 0x8e24aa 0xfb8c00)
for i in "${!colors[@]}"; do
  c="${colors[$i]}"
  ffmpeg -loglevel error -y -f lavfi -i "color=c=$c:size=300x450" -frames:v 1 "$OUT/img/poster$i.png"
  ffmpeg -loglevel error -y -f lavfi -i "color=c=$c:size=1280x720" -frames:v 1 "$OUT/img/backdrop$i.png"
  ffmpeg -loglevel error -y -f lavfi -i "color=c=$c:size=200x200" -frames:v 1 "$OUT/img/logo$i.png"
done

echo "Media generated in $OUT"
