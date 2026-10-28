# 2026-10-28 — Phase 2a: vinden

Status: **built, on the tv; waiting for Jordy to try it.** Branch `feat/phase-2a`. Done: migrations,
name cleaning, smaller cards, countries + preferred country, search, debug EPG check, catch-up label,
settings redesign, BUIS name and logo. Open: run the EPG check on the tv and decide the guide.

## What the real catalog looks like (measured on the tv, 2026-10-28)
| | Count | Notes |
|---|---|---|
| Live channels | 16,130 in 484 categories | 99% of categories start with a country tag |
| Films | 69,053 in 168 categories | 0.1% without poster |
| Series | 20,560 in 122 categories | |
| Dutch live channels | 1,198 (~7%) in 38 categories | 45% have an EPG id |
| All live channels with an EPG id | 35% | explains the empty guide |

Naming patterns (generic shapes, no provider data in the repo):
- Tags use the Unicode bar `┃` (U+2503), sometimes `|` or a space before the closing bar: `┃NL┃ …`, `┃MULTI ┃ …`, `|LINGO| …`.
- Country tags: two or three letters (`NL`, `UK`, `USA`), sometimes with a language (`CA EN`, `CA FR`). Non-country tags: `PPV`, `UCL`, `ADULT`, `F1TV`, `MXGP`.
- Language tags on VOD: `MULTI`, `LINGO`, `MULTI / LINGO`, `NL-SUB`, `NL-AUDIO`, `EN`.
- Item names repeat the tag: `┃NL┃ Kill Bill: Vol. 1`.
- Quality suffixes on live channels: `HD` (4.6k), `FHD` (1.8k), `4K`, `HEVC`, `UHD`, `SD`, `RAW`.
- `⏺ʳᵉᶜ` / `⏺` marks catch-up ("terugkijken").

## Scope, in build order
1. **Name cleaning** (`NameParser`, pure Kotlin, unit-tested on the patterns above).
   Splits a raw name into display title, tags (country, language), quality and a catch-up flag.
   `┃MULTI / LINGO┃ APPLE TV+` → title "Apple TV+", label "Multi". Applied at sync time and stored,
   so the UI and search never parse at runtime.
2. **Smaller cards.** Channel rows ~64dp (was 96), posters ~120dp wide (was 150), channel names on
   up to two lines. Big artwork stays on the detail screens.
3. **Countries and preferred country.**
   - Categories get a `country` column, filled from the tag at sync.
   - Instellingen: "Voorkeurslanden" (default NL) and "Alleen voorkeurslanden tonen" (default on).
   - Live TV, Films, Series: preferred countries first; with the filter on, only those plus
     untagged categories. A "Alle landen" entry at the end of the category list turns it off for
     that visit.
   - Settings stored with DataStore Preferences (new dependency `datastore-preferences`).
4. **Search.** Room FTS4 table over cleaned titles (~105k rows), filled during sync. "Zoeken" in
   the rail; results as rows per type (Zenders, Films, Series), preferred countries ranked first.
   Typing via the on-screen keyboard. Voice search only if the tv offers a speech recogniser.
5. **EPG check, then decide.** Spike on the tv: download size and duration of `xmltv.php`, and
   coverage for the Dutch channels. If coverage is good: stream-parse it (XmlPullParser) into
   Room, preferred-country channels only, refreshed once a day. If it isn't: write down the
   options (external public guide, matching by channel name) and decide with Jordy.
6. **Catch-up label.** Show "Terugkijken" on channels marked `⏺`. Playing catch-up (`tv_archive`,
   timeshift URLs) is not in 2a.

## Must happen first: real Room migrations
Phase 1 uses destructive migrations. 2a changes the schema (country column, cleaned names, FTS
table) and Jordy now has favourites and resume positions on the tv. Before the first 2a build:
switch to real migrations (catalog tables may be rebuilt by the next sync; `favorite` and
`playback_position` must survive), with a migration test.

## Done when (on the real tv)
- After launch, a Dutch channel is playing in at most 3 clicks, without scrolling past other countries.
- Channel, film and series names show without tags; Apple TV+ reads "Apple TV+" with a "Multi" label.
- Searching "npo" or a film title shows results within ~0.5s, over the full catalog.
- Favourites and resume positions from phase 1 are still there after updating.
- EPG: coverage for Dutch channels measured, and either working or a decision logged.

## Tests that matter
- `NameParser` on every pattern above, including the odd ones (`┃MULTI ┃`, `|USA┃`, missing closing bar).
- Country filter and ordering with real-looking category lists.
- Room migration from schema 1 keeps favourites and positions.
- Search ranking: preferred country first; a search over 100k rows stays under the time budget (JVM test).

## Not in 2a (backlog, in rough priority)
- Start on the last watched channel; "Recent bekeken" row (TiviMate-style).
- Merge duplicate films/series that exist in several language versions (NL / US / MULTI) into one tile with a language choice.
- Catch-up playback.
- Genre preferences: phase 3, learned from viewing history.
