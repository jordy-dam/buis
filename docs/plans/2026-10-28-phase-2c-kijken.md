# 2026-10-28 — Phase 2c: kijken

Status: **built and tested on the emulator; not on the tv yet.** Branch `feat/phase-2a`. Follows the
UX review `docs/reviews/2026-10-28-ux-review-2b.md` (its retrial is at the bottom of that file).
Decisions: `.agents/decisions/architecture.md` (guide in Room) and `product.md` (2c layout).

## What was built
- **Full guide**: `xmltv.php` parsed as a stream into Room (schema 4) for the household's preferred
  countries, now − 2 h to now + 36 h. Refreshed when the shell starts and the guide is older than
  20 h, after every catalog sync, and via Instellingen → "Gids vernieuwen" (with status). Streams
  without an EPG id borrow the id of a stream with the same cleaned name in the same country.
  Short EPG stays as the fallback.
- **Compact Live TV**: one-line 48dp rows with number, logo, name, `NU · title`, 2dp progress; a
  fixed strip above the list with the focused channel's slot and "Straks". The same `NU` line on
  Home's "Favoriete zenders", on Favorieten, and in the player overlay.
- **Rail**: collapses to 72dp icons while focus is in the content; a tab opens after 150 ms of rest
  or at once on OK/Right. Zoeken is first, under the avatar, and opens the keyboard on entry.
- **Density**: posters 104dp, one vertical rhythm (`Rows` in `Theme.kt`), no page titles on Films
  and Series, poster fallback as a title card.
- **Home**: title and time left under the focused "Verder kijken" tile.
- **Series detail**: Down from the buttons lands on the season shown.
- **Mock server**: serves `xmltv.php`, with one stream without an id that borrows a sibling's.

## Tested
- Unit (73, all green): XMLTV import (window, wanted channels, offsets, broken and duplicate
  programmes), aliases within a country only, now/next from Room with an exclusive stop time,
  migration 3→4 keeping profiles and their data, a 200k-programme guide read in ~1.2 s on the JVM.
- Emulator walk-through (14 screens): everything above, plus the guide re-imported after a catalog
  sync (604 streams; "NPO 2 HD" uses npo2.nl through its alias).
- `./gradlew lint`: no new warnings.

## For Jordy on the tv
1. Open Instellingen → "Gids vernieuwen": how long it takes, and how many channels have a guide.
2. Live TV at your viewing distance: are the 48dp rows readable, and is it calmer now?
3. Does walking down the rail feel smooth on the TCL?
4. Then: OK to read the real VOD category names (counts only in chat, nothing in the repo) to decide service rows.

## Not in 2c
EPG grid, catch-up playback, service rows (after the measurement), category hide/reorder, ratings.
