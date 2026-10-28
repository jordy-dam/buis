# 2026-10-28 — Phase 2d: bladeren

Status: **built and tested on the emulator (debug and release); not on the tv yet.** Branch `feat/phase-2a`.
Decisions: `.agents/decisions/architecture.md` (release build with R8) and `product.md` (2d browsing).

## Why
Jordy's feedback on 2c on the tv: sometimes slow; channel-row labels jump; favourite marker at the
end; wants a page per service and genre filtering.

## Measured first
- **Speed** (tv, debug build): 20% janky frames, p90 85 ms, p99 400 ms; 1,363 slow-UI-thread frames,
  1 slow bitmap upload. Not images, but the debuggable build on a 32-bit CPU.
- **Catalog** (tv database, counted and deleted): 82 NL film categories (19 services, about a dozen
  genres such as Actie, Horror, Drama), 35 NL series categories (16 services), 19,826 NL films,
  90% of NL series with a genre.

## What was built
- **Release build with R8** for the tv (7.6 MB instead of 54 MB), signed with the debug key so it
  installs over the existing app with all data.
- **Channel row**: heart before the number, best picture quality and catch-up (↺) in a fixed slot
  on the right, stable columns, progress bar at 45% opacity.
- **Films and Series as list + grid**: category list with "Diensten", "Genres", "Overig"; the whole
  category as a 4-column poster grid; Back grid → list → rail → Home. Opens on the first service.
- **Series genre chips** ("Alle · Drama · Misdaad …"), English and Dutch spellings merged.

## Tested
- Unit (79, all green): grouping on real-looking names, genre splitting/merging, quality pick, plus the earlier 73.
- Emulator walk-through, debug and release: channel rows, Films/Series list + grid, chips, Back chain;
  release plays films and live with the FFmpeg decoder loaded; no crashes.

## For Jordy on the tv
1. Does it feel faster? (I'll compare gfxinfo with the debug baseline.)
2. Films/Series: are the right categories under "Diensten" and "Genres"?
3. Channel rows: heart, quality label and ↺ readable from the couch?

## Not in 2d
Film genre chips (needs per-title data: phase 3, with TMDB or `get_vod_info`), EPG grid, catch-up playback.
