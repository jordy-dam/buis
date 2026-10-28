# 2026-10-28 — Phase 2f: eigen gezicht

Status: **built and tested on the emulator (debug and release); not on the tv yet.** Branch `feat/phase-2a`.
Decision: `.agents/decisions/product.md` (2f).

## What was built
- **Logo**: Jordy's round logo cut from the screenshot (`docs/brand/buis-logo-source.png` →
  `docs/brand/make-logo.sh` → `buis-logo.png`), as the app icon, the launcher banner (the logo on
  its own blue) and the header of "Wie kijkt er?" and the login screen.
- **Avatars**: five drawn figures (cat, fox, robot, owl, monster) on the avatar colour, picked under
  "Gezicht" in the profile editor (schema 6, `profile.face`; existing profiles keep their letter).
  New profiles get a face and colour nobody uses; a fresh household starts with the cat.
- **One tile per film**: versions of the same title and year collapse into one tile ("2 versies");
  the detail screen shows "Versie: Nederland · NL sub …" and starts the one watched last. Search and
  "Recent toegevoegd" show one entry per film too.
- **Text one step smaller** (13sp floor).
- **Screen stays on while playing**: the TCL's screensaver (after 10 min without a key) no longer
  interrupts a film or channel; when paused it may still come.

## Tested
- Unit (90, all green): version labels and default pick, grouped grid incl. remakes and the genre
  filter, migration 5→6, default avatar per new profile, plus everything before.
- Emulator walk in debug and release: logo on the picker, one search hit for a film with two
  versions, the version row on the detail screen, the "Gezicht" row; no crashes.
- Not visible on the emulator: the launcher banner (its launcher wants a Google sign-in first). Check on the tv.

## For Jordy on the tv
1. The BUIS tile in the Google TV apps row: the new banner.
2. Films: duplicates gone, version choice on the detail screen.
3. Profiel bewerken → Gezicht: pick one of the five.
4. Watch something for more than 10 minutes without touching the remote: no screensaver.
