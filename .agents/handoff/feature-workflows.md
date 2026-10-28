# Handoff — feature work

## TL;DR for the next person
Phase 1 runs on the real tv (TCL Smart TV Pro, Android 14, 32-bit) with Jordy's real subscription.
Phase 2a "vinden" is on the tv but not yet tried by Jordy. Phase 2b "huishouden" (profiles, Home,
PIN, kids profile) is built and tested on the emulator, not on the tv:
`docs/plans/2026-10-28-phase-2b-huishouden.md`. 2b is installed on the tv. Phase 2c "kijken" (full EPG,
compact channel list, collapsing rail, search on top) is built and emulator-tested, not on the tv:
`docs/plans/2026-10-28-phase-2c-kijken.md`. Next: Jordy tests 2b + 2c on the tv; then measure the
real VOD category names and decide service rows.

## Current phase
Phase 1 — done and tested on the real tv. Phase 2a — built and installed on the tv (not started yet
 by Jordy); open: EPG check on the tv. Phase 2b — on the tv. Phase 2c — built, emulator-tested, not on the tv.

## What's working (emulator + mock server)
- Login with validation and Dutch error states (wrong password, expired, unreachable, not Xtream)
- Catalog sync into Room with progress (13.5k items in ~1s locally); daily re-sync on launch
- Live TV: categories | channels with logo + now-playing (short EPG); zapping ↑/↓ with .m3u8 → .ts fallback
- Films and Series: category rows, detail screen with hero, seasons/episodes, play
- Player: Media3 + FFmpeg audio, resume for films and episodes (also after a force-stop)
- Favourites: long-press OK or Menu on any tile; Favorieten tab; zapping inside favourite channels
- Back chain player → detail → list → Live TV → "Afsluiten?" dialog; focus restored on return
- Instellingen (bottom of the rail): "Voor <naam>" (countries, filter) and "Voor iedereen" (catalog, subscription, "Uitloggen" wipes the database incl. profiles)
- Profiles (2b): "Wie kijkt er?" on launch with 2+ profiles, avatar at the top of the rail, per-profile favourites/positions/countries, optional PIN, kids profile hiding adult categories
- Home (2b): start tab with "Verder kijken", "Favoriete zenders", "Recent toegevoegd"
- Guide (2c): full XMLTV in Room, refreshed on start (>20 h), after a sync and via "Gids vernieuwen"; `NU ·` + progress on every channel, "Straks" strip, aliases for channels without an EPG id
- Layout (2c): one-line 48dp channel rows, rail collapses to icons in the content, Zoeken first in the rail, 104dp posters

## What's not (yet)
- EPG grid, hide/reorder categories, ratings, service rows (after measuring) and phase 3 (suggestions)
- No auto-play of the next episode
- No themes (decided against, see `product.md`)

## Emulator gotchas
- The Google TV system image ships with `hw.keyboard=no`: the Mac keyboard does nothing until it's
  set to `yes` in `~/.android/avd/tv_1080p.avd/config.ini` (done on this machine).
- After a cold boot Google TV may show "Choose an account" on top; press Back and relaunch the app.
- Screenshots: `adb exec-out screencap -p` can hang; guard it with a timeout that returns as soon as the capture is done (a plain `sleep 12; kill` costs 12 s per shot).
- The mock server serves `xmltv.php` too (2c); "NPO 2 HD" has no EPG id and borrows "NPO 2"'s after a catalog sync.
- When restarting the mock server, kill only the listener (`lsof -ti tcp:8080 -sTCP:LISTEN`): the
  emulator process also holds a connection on 8080 and dies with a plain `lsof -ti tcp:8080`.

## Decisions made & why
- `.agents/decisions/architecture.md`: Media3 pinned to 1.9.0 for FFmpeg, no Retrofit, Tink credentials, fail-fast 4xx, SDK 37
- `.agents/decisions/product.md`: phase split 2a/2b, BUIS name, 2b profiles + Home + kids profile
- `.agents/decisions/architecture.md` (2b): active profile = last used, settings on the profile row, PIN as household lock

## How to run locally
See `.agents/onboarding/new-dev.md` (mock server: `./mockserver/generate-media.sh`, then `./gradlew :mockserver:run`).

## Open questions
- Does `xmltv.php` cover the Dutch channels? Only 45% of them have an EPG id (spike in 2a).
- Do the `⏺` channels expose `tv_archive` for catch-up? (not stored yet)

## Real-tv notes
- Install over Ethernet: USB debugging on, `adb connect 192.168.68.91:5555` (see onboarding).
- With the emulator also running, `./gradlew installDebug` installs on both: use `adb -s <device> install -r`.
- The tv's default keyboard was the Google TV remote bridge (no on-screen keyboard); switch to the
  TCL keyboard in the tv settings, or type via the Google TV app on a phone.

## Who to ask
- Jordy: owner, tests on the real Google TV
