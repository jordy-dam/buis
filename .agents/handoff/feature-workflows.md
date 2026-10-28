# Handoff — feature work

## TL;DR for the next person
Phase 1 is built and verified on the Android TV emulator (1080p) against the mock Xtream server,
on branch `feat/phase-1` (not committed yet at time of writing). It has **not** been tested on the
real Google TV yet; that test decides whether phase 2 can start. Report and test list:
`docs/plans/2026-10-28-phase-1.md`.

## Current phase
Phase 1 — Works on my tv: built, emulator-verified, waiting for the real-tv test.

## What's working (emulator + mock server)
- Login with validation and Dutch error states (wrong password, expired, unreachable, not Xtream)
- Catalog sync into Room with progress (13.5k items in ~1s locally); daily re-sync on launch
- Live TV: categories | channels with logo + now-playing (short EPG); zapping ↑/↓ with .m3u8 → .ts fallback
- Films and Series: category rows, detail screen with hero, seasons/episodes, play
- Player: Media3 + FFmpeg audio, resume for films and episodes (also after a force-stop)
- Favourites: long-press OK or Menu on any tile; Favorieten tab; zapping inside favourite channels
- Back chain player → detail → list → Live TV → "Afsluiten?" dialog; focus restored on return
- Instellingen (bottom of the rail): subscription info, catalog status, "Catalogus vernieuwen", "Uitloggen" (wipes the database)

## What's not (yet)
- Everything in phase 2 and 3 (profiles, Home, search, EPG grid, hide/reorder categories, suggestions)
- No auto-play of the next episode
- No themes (decided against, see `product.md`)

## Emulator gotchas
- The Google TV system image ships with `hw.keyboard=no`: the Mac keyboard does nothing until it's
  set to `yes` in `~/.android/avd/tv_1080p.avd/config.ini` (done on this machine).
- After a cold boot Google TV may show "Choose an account" on top; press Back and relaunch the app.
- When restarting the mock server, kill only the listener (`lsof -ti tcp:8080 -sTCP:LISTEN`): the
  emulator process also holds a connection on 8080 and dies with a plain `lsof -ti tcp:8080`.

## Decisions made & why
- `.agents/decisions/architecture.md`: Media3 pinned to 1.9.0 for FFmpeg, no Retrofit, Tink credentials, fail-fast 4xx, SDK 37
- `.agents/decisions/product.md`: phase 1 rail without Home/Zoeken

## How to run locally
See `.agents/onboarding/new-dev.md` (mock server: `./mockserver/generate-media.sh`, then `./gradlew :mockserver:run`).

## Open questions
- Does the real provider serve HLS, or only .ts? Zap times on the tv will tell (logcat tag `Zap`, debug builds).
- Does the tv decode AC3/EAC3 itself, or does it fall back to FFmpeg? Check for audio on a channel with AC3.

## Who to ask
- Jordy: owner, tests on the real Google TV
