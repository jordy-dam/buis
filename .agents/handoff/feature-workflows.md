# Handoff — feature work

## TL;DR for the next person
Phase 1 runs on the real tv (TCL Smart TV Pro, Android 14, 32-bit) with Jordy's real subscription.
Phase 2a "vinden" is on the tv but not yet tried by Jordy. Phase 2b "huishouden" (profiles, Home,
PIN, kids profile) is built and tested on the emulator, not on the tv:
`docs/plans/2026-10-28-phase-2b-huishouden.md`. 2b is installed on the tv. Phase 2c "kijken" (full EPG,
compact channel list, collapsing rail, search on top) is built and emulator-tested, not on the tv:
`docs/plans/2026-10-28-phase-2c-kijken.md` (on the tv). Phase 2d "bladeren" (release build with R8,
Films/Series as list + grid with Diensten/Genres, series genre chips, calmer channel rows) is built
and emulator-tested: `docs/plans/2026-10-28-phase-2d-bladeren.md`. 2d is on the tv (release); janky
frames went from 20% to 5%. Phase 2e "verfijnen" (quiet focus, one step smaller, Home channel
cards, avatar top left, "Profiel bewerken" in Instellingen, film genres via background
`get_vod_info`) is built and emulator-tested: `docs/plans/2026-10-28-phase-2e-verfijnen.md`.
2e is on the tv. Phase 2f "eigen gezicht" (logo, icon and banner, five drawn avatars, one tile per
film with a version choice, text one step smaller, screen kept on while playing) is built and
emulator-tested: `docs/plans/2026-10-28-phase-2f-eigen-gezicht.md`.

## Current phase
Phase 1 — done and tested on the real tv. Phase 2a — built and installed on the tv (not started yet
 by Jordy); open: EPG check on the tv. Phase 2b to 2e — on the tv. Phase 2f — built, emulator-tested (debug and release), not on the tv.

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
- Browsing (2d): Films/Series = category list (Diensten, Genres, Overig) + grid; series genre chips; channel rows with heart before the number and a fixed quality/catch-up slot
- Look (2f): round logo as icon/banner/picker header (`docs/brand/make-logo.sh`), five drawn avatars ("Gezicht"), one tile per film with versions on the detail screen, screen kept on while playing
- Refinements (2e): quiet focus on list rows, one step smaller type/tiles, 5-column grid, Home channel cards, avatar top of the rail, "Profiel bewerken" in Instellingen, film genre chips fed by `MovieEnricher` (status under Instellingen → "Filmgegevens")

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
- Every `adb install` stops the running app on the tv ("package updated" in `dumpsys activity exit-info nl.jordy.iptv`); only install when Jordy says so, ideally while nobody watches.
- The TCL starts Google TV's screensaver after 10 min without a key press (`screen_off_timeout` 600000); the player keeps the screen on while playing (2f).
- The tv runs the **release** build (`assembleRelease`, signed with the debug key): `adb -s 192.168.68.91:5555 install -r app/build/outputs/apk/release/app-release.apk`. Release isn't debuggable, so `run-as` (reading its database) doesn't work there.
- Frame times on the tv (`adb shell dumpsys gfxinfo nl.jordy.iptv`, `… reset` to start fresh): debug build 20% janky, p90 85 ms, p99 400 ms; release + R8 5.2% janky, p90 36 ms, p99 150 ms.
- With the emulator also running, `./gradlew installDebug` installs on both: use `adb -s <device> install -r`.
- The tv's default keyboard was the Google TV remote bridge (no on-screen keyboard); switch to the
  TCL keyboard in the tv settings, or type via the Google TV app on a phone.

## Who to ask
- Jordy: owner, tests on the real Google TV
