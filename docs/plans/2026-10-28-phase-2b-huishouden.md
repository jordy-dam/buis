# 2026-10-28 — Phase 2b: huishouden

Status: **built and tested on the emulator; not installed on the tv yet.** Branch `feat/phase-2a`
(on top of 2a, which Jordy hasn't tested on the tv yet either). Decisions:
`.agents/decisions/architecture.md` and `product.md` (2026-10-28 entries on profiles).

## What was built
- **Profiles** in Room (schema 3): name, avatar colour, optional PIN, preferred countries,
  "alleen voorkeurslanden", kinderprofiel. Favourites and resume positions per profile.
  The watching profile is the last used one (`ProfileRepository.current`).
- **Migration 2→3**: existing favourites and positions move to "Profiel 1" (NL, filter on);
  the 2a DataStore settings are copied into it once. The catalog re-syncs after the update.
- **"Wie kijkt er?"** on launch with more than one profile; focus on the last used. Add, edit
  (name, colour, PIN, kinderprofiel) and remove under "Profielen beheren".
- **Rail**: avatar + name at the top; OK opens the picker. Home is the new first tab and start
  screen: "Verder kijken", "Favoriete zenders", "Recent toegevoegd" (preferred countries).
- **Instellingen** split into "Voor <naam>" (countries, filter) and "Voor iedereen".
- **PIN**: on-screen keypad, asked when picking a PIN profile. With a kids profile present,
  managing profiles asks a grown-up's PIN.
- **Kinderprofiel**: adult categories (ADULT / XXX / 18+ in the provider's name) hidden in
  Live TV, Films, Series, search and Home, also under "Alle landen tonen".

## Tested
- Unit (68 tests, all green): migration 1→2, 2→3, 1→3 with data; profile isolation (favourites,
  positions, "Verder kijken"), cascade on delete, last profile can't be deleted, logout wipe
  recreates a profile; PIN hash/verify; kids filter; adult name detection; "Recent toegevoegd".
- Emulator + mock server: a 2a database with a favourite and a position, updated to 2b → both on
  "Profiel 1". Second profile "Bas" with its own favourite (RTL 4) and country (België); switching
  both ways via the rail in two OK presses; cold start focuses the last used profile; wrong and
  right PIN; management lock; kids profile hides ADULT; Back from the picker returns to the shell.
- `./gradlew lint`: no new errors.

## Known limitations
- "Verder kijken" only shows episodes of series that were opened on this tv (episodes are fetched
  per series); a film disappears from it once finished (resume policy clears the position).
- Right after the update the app may open on Live TV once: Android restores the 2a tab state.
- The adult match is by category name only; a provider that names them differently slips through.
- Not in 2b: ratings, hiding/reordering categories, EPG grid, voice search.

## For Jordy on the tv
1. Test 2a first (catalog refresh, "EPG controleren"), so a 2a issue isn't blamed on profiles.
2. Install 2b (`adb -s 192.168.68.91:5555 install -r app/build/outputs/apk/debug/app-debug.apk`;
   `./gradlew installDebug` installs on every connected device, the emulator too).
3. Check your favourites and resume positions are on "Profiel 1"; rename it.
4. Add a second profile, mark something as favourite, set a country, switch back and forth.
5. Real catalog: do the provider's adult categories disappear on a kids profile?
