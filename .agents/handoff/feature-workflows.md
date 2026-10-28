# Handoff — feature work

## TL;DR for the next person
Phase 1 runs on the real tv (TCL Smart TV Pro, Android 14, 32-bit) with Jordy's real subscription:
login, sync, zapping, VOD, resume, audio and favourites all work (Jordy, 2026-10-28). Next is
phase 2a "vinden" (names, countries, search, EPG check): `docs/plans/2026-10-28-phase-2a-vinden.md`.
Start with real Room migrations — the tv now holds favourites and positions.

## Current phase
Phase 1 — done and tested on the real tv. Phase 2a — built and installed on the tv (not started yet
 by Jordy); open: EPG check on the tv. The app is now called BUIS.

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
- Does `xmltv.php` cover the Dutch channels? Only 45% of them have an EPG id (spike in 2a).
- Do the `⏺` channels expose `tv_archive` for catch-up? (not stored yet)

## Real-tv notes
- Install over Ethernet: USB debugging on, `adb connect 192.168.68.91:5555` (see onboarding).
- The tv's default keyboard was the Google TV remote bridge (no on-screen keyboard); switch to the
  TCL keyboard in the tv settings, or type via the Google TV app on a phone.

## Who to ask
- Jordy: owner, tests on the real Google TV
