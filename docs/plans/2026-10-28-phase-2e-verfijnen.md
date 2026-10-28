# 2026-10-28 — Phase 2e: verfijnen

Status: **built and tested on the emulator (debug and release); not on the tv yet.** Branch `feat/phase-2a`.
Decisions: `.agents/decisions/architecture.md` (film details in the background) and `product.md` (2e).

## Why
Jordy's feedback on 2d on the tv: focus and progress share the accent and the focus border is
heavy; Home shows barely one favourite channel; everything could be a notch smaller; where do
per-profile settings live; Films have no genres; avatar top left, a round logo to come.

## What was built
- **Quiet focus for list rows** (channels, categories, settings, chips, rail): a lighter surface and
  a thin white line. Artwork keeps the accent outline. The progress bar is the only accent in a row.
- **One step smaller**: type (secondary 14sp, titles 16sp), rail 176/64dp, channel rows 42dp,
  posters 92dp, grid 5 × 84dp, category list 208dp.
- **Home**: favourite channels as 240×60 cards (logo, name, programme, thin progress).
- **Rail**: starts with the avatar; no wordmark there.
- **Instellingen → "Voor <naam>" → "Profiel bewerken"**, with the same PIN checks as the picker.
- **Film genres**: `MovieEnricher` fetches `get_vod_info` for films in the preferred countries
  (250 ms apart, paused while playing, resumes, retries failures after a day) into `movie_info`
  (schema 5). Films get genre chips as details arrive; Instellingen shows "Filmgegevens: x van y".

## Tested
- Unit (85, all green): enricher (only preferred countries, once, failures retried after a day,
  spaced out and paused while playing), film genre filter, migration 4→5, PIN gates for editing
  from Instellingen, plus everything before.
- Emulator walk-through in debug and release: all of the above, no crashes.

## For Jordy on the tv
1. Size at viewing distance: is 14sp secondary text still readable?
2. Instellingen → "Filmgegevens": the first fill takes about 1.5 h in the background (only while
   nothing plays). Film genre chips appear per category as it goes.
3. Logo: a round logo as 1024×1024 PNG with a transparent background, plus a 320×180 PNG banner
   for the Google TV launcher. Drop them in `docs/brand/` and I'll wire them in.

## Not in 2e
TMDB, audio/subtitle language per profile, baseline profile, EPG grid.
