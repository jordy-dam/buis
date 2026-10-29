# 2026-10-29 — Phase 2h: the ✓ ideas from the brainstorm

Status: **built, emulator-tested, on the tv since 2026-10-29 15:47 (release 779bdd9); Jordy's tv test pending. Screensaver not switched on on the tv.** Branch `feat/pauzemoment`, on top of
Pauzemoment. Jordy picked these in `docs/brainstorm.md` and said "bouw het allemaal maar". Each part is
its own commit, so each can be tested or reverted on its own. Schema 15 → 20.

| Part | Commit | Where |
|---|---|---|
| Geluid, ondertitels, klok | 4258e30 | Player: Down (VOD) / Right (live) opens "Opties"; Instellingen: default languages |
| Bedtijd | 748bed8 | Instellingen → Bedtijd, Bedtijd op; card app-wide |
| Kijkgeschiedenis + weekoverzicht | 98a6cef | Instellingen → Kijkgeschiedenis |
| Zenderstatus | 3f52c68 | Instellingen → Voor iedereen → Zenderstatus |
| Intro overslaan | 803cdd7 | Episodes, learned per series |
| Stemming en lengte | e8275a3 | Films: chips "Licht", "Spannend", length chip |
| Instant replay | a5b432b | Live: hold Left 10 s back, OK to live |
| Easter egg | d1f9c4b | "Wie kijkt er?": ↑ ↑ ↓ ↓ ← → ← → OK |
| Ronde PIN-toetsen | 7a77aef | Every PIN dialog |
| Screensaver | f90b431 | DreamService; debug preview in Instellingen |

## How each part behaves
- **Opties in the player:** Geluid and Ondertitels step through the stream's tracks for this title only;
  Klok switches a subtle clock in the bar (per profile). Profile defaults pick audio/subtitle language when
  the stream has it ("Standaard" = the tv's language). Subtitles render with Media3's SubtitleView and
  move above the bar while it shows.
- **Bedtijd:** time (21:00–00:00, off by default) and evenings (Zo–do, Ma–vr, elke dag). Once an evening;
  an evening runs noon–06:00. Doorkijken focused; Naar bed closes BUIS. Bedtijd and reminders queue: the
  card that's up stays until answered.
- **Kijkgeschiedenis:** everything watched ≥ 1 minute (playing time) per profile, 90 days; OK plays again.
  Weekoverzicht over 7 days: hours per kind, top channels, films/episodes, longest binge, busiest day.
- **Zenderstatus:** each live session logged (stall = buffering ≥ 3 s after start; failed after HLS/TS
  fallback). Troubled = ≥ 3 sessions, half troubled. Proposal: a steady sibling (same cleaned name),
  else an untried one. OK replaces (favourites and zapping play it; guide and catch-up stay the original's).
- **Intro overslaan:** a forward skip in the first 10 minutes + 30 s of watching marks the series' intro end.
- **Stemming/lengte:** moods are genre groups; length from `duration_secs` (now stored, details re-fetched).
  Films without details can't be judged; the grid says so.
- **Instant replay:** Left in live acts on release (short = vorige zender), held = 10 s back into a minute
  of back buffer; only where the stream can seek (HLS), otherwise the bar says so.
- **Screensaver:** three posters of the profile, drifting, changing every 12 s, clock. Needs
  `adb shell settings put secure screensaver_components nl.jordy.iptv/.ui.dream.BuisDream` on Google TV.

## Tested
- Unit tests 190+ green (TrackLabels, Bedtime, WatchLog/WeekSummary, ChannelHealth/StallDetector,
  IntroLearner, FilmFilters + an in-memory query test, TestbeeldCode, migrations 14 → 20). Lint: the old
  HomeScreen error only; one new warning (media3-ui has a newer version, like the other media3 modules).
- Emulator, each part by hand: audio switch NL/EN and Dutch subtitles on a mock film (mock now has two
  audio tracks + subtitles); clock; bedtime card at 21:10 and Naar bed closing BUIS; history row + replay;
  Zenderstatus proposal for a broken mock "NPO 1 SD" (ids ending in 99 fail) and the replacement playing;
  intro learned at 1:04 and offered on the next episode; mood/length chips and empty message; replay in
  live and short Left still vorige zender; testbeeld; round keypad; screensaver via the debug preview.

## Not tested / known gaps
- Real live HLS for instant replay (the mock's HLS is a VOD playlist, so "live" there is 0:00).
- The screensaver as a real dream (the Google TV emulator isn't set up and has no root).
- A reminder can still open over the player's options panel (dialog over panel, same corner).
- Search results and the live channel list don't show quality badges, so "NPO 1" HD and SD look the same.
- Search history (part of the history idea) wasn't built; only watch history.

## After Jordy's first look on the tv (2026-10-29)
- Opties panel felt stuck: rows only took focus when there was a choice, so on single-track live only Klok
  reacted. Every row takes focus now (20a32c9).
- Instellingen descriptions cut, then replaced by an icon per row; "Versie" (name · commit · day) added.
- Icons instead of words where they say it (fee9212): player bars, Opties panel, rail, favourites (heart),
  terugkijken (history), reminder (bell) and followed (eye) in the guide. Recording has no icon: BUIS can't record.
- Screensaver can't be picked in the tv's own settings: Google TV only offers Ambient mode there. It's set
  with one adb command (`settings put secure screensaver_components …`), still waiting for Jordy's OK.

## Round after the tv: lost profiles, one player surface (2026-10-29, evening)
- **"Profiles gone" at the fifth** (6d83b0a): no data was lost. Five profiles + "+" wrap into two rows,
  and the picker's FlowRow had less height than two rows need, so it silently left out the second row
  (FlowRow's default overflow). Reproduced on the emulator (5 rows in the database, 3 tiles on screen).
  Plain rows now; a Robolectric Compose test renders 1–8 profiles at 960 × 540 dp and fails on the old
  layout. The profiles come back on the tv with this build.
- **Picker** (d1f7bee): kids and PIN as badges on the avatar (ChildCare, Lock) instead of caption lines,
  more air under the title; "Profielen beheren" lines up the same way.
- **Icons** (8f525f6): the rail's icons sat below their labels (top-aligned row) and the selected one was
  black (Unspecified tint); SettingRow icons take the row colour and share the title's centre line.
- **Buttons** (570f71b): pop-up/player choices one size (48 dp); tall labels sat ~3 dp high because a tv
  Button lays its content at the top, now grown by padding; light focus fill on the translucent
  `PopupSurface` (SurfaceRaised at 82 %).
- **Player** (88b64a7): one bar with focusable controls; paused, it stays; audio/subtitles open in place
  above their button; the clock sits in the bar (it was only visible in a 3 s overlay that the Opties panel
  also hid, so switching it on showed nothing). Pop-ups in the player take focus like the reminder.
  Decision in `.agents/decisions/product.md`; spec rule "One place, one layout" rewritten.
- **Slower** (761c159): the film-details re-fetch after the 19 → 20 migration wrote every film on its own,
  which re-ran the Films grid's paging query, the genre chips and Instellingen 4× a second for ~1.5 h.
  Now written in groups of 20. Ticks in the player are plain arithmetic (no I/O); the player's position
  ticker now only runs while the bar shows; CornerCard dropped its intrinsic-width pass.
- Not verified: the 20k-film load itself (the mock has 4 films with a country), the black Opties icons
  Jordy saw (white on the emulator; tints are now explicit everywhere), quality badges (mock list has none).
