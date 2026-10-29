# 2026-10-29 — Pauzemoment

Status: **planned, not built.** Branch `feat/pauzemoment`. Idea from `docs/brainstorm.md`.

## Why
Jordy: after an hour of a film or a run of episodes, BUIS should take a short break by itself so
everyone can go to the toilet, get a drink or a snack. One minute, then it plays on. Only when
you set it, only for films and series, and with next-episode autoplay it counts the total time.

## What to build

### 1. Setting per profile
- Instellingen → "Voor <naam>", right under Slaaptimer: **"Pauzemoment"**, cycling
  `Uit · 45 min · 60 min · 90 min` (same row pattern as Slaaptimer). Default **Uit** for every profile.
- Help text: "Na zo lang kijken pauzeert BUIS een minuut, zodat iedereen even kan plassen, drinken
  of iets pakken. Alleen bij films en series."
- New column `breakMinutes` on the profile (default 0), schema 12 → 13 with a migration test.
  `ProfilePrefs` and `ProfileRepository.setBreakMinutes` mirror `sleepMinutes`.

### 2. When the break comes (`player/BreakTimer.kt`, pure logic, time passed in like `SleepTimer`)
- **Counts playing time**, not wall time: only while the player is actually playing a film or episode.
  Live tv never counts. This makes "only longer than an hour" fall out by itself: a single
  40-minute episode never reaches 60 minutes, three in a row with autoplay do.
- The count lives for one player session: it carries over when autoplay starts the next episode
  (same `PlayerViewModel`), and starts at zero when you open the player again.
- **A break you took yourself resets it**: a manual pause of 3 minutes or more, or leaving the player.
- When the count reaches the setting, the break is **due**. Where it happens:
  - **Episode with a next one coming:** wait for the end. The next-episode card becomes the break
    card; after the minute the next episode starts. If the episode ends within 15 minutes, this is
    always where it goes; if more than 15 minutes are left, it pauses now (see film).
  - **Film or last episode:** pause right away, unless it ends within 15 minutes (then skip: it's
    nearly over and the credits are a break anyway).
  - Not while the next-episode card is already asking "Kijk je nog?" (that waits for a key already).
- After a break the count starts at zero, so a long evening gets a break every hour.

### 3. The break card
- Playback pauses; the card sits in the middle of the screen over the paused picture:
  three small drawn icons in a row (toilet, bottle, snack bowl; drawn in Compose like
  `AvatarFaces`, no emoji, in `TextSecondary`), title **"Even pauze"**, and
  "Verder over 0:58" counting down from one minute.
- Keys: **OK = verder nu** (primary hint), **Terug = overslaan**, which resumes too and just means
  "no break this time". Any other key keeps the break (someone walking past the remote shouldn't end it).
- At zero it resumes by itself; between episodes it starts the next one.
- Reuse `KeyHint` and the card style of `NextUpPanel`.

### 4. With the sleep timer
- The two stay separate. The sleep timer keeps counting from the last key; a break with nobody
  pressing anything brings it one minute closer, which is right (nobody is there).
- If the sleep warning and the break card would show at once, the sleep warning wins (it's the one
  that stops playback).

## Done when
- Unit tests for `BreakTimer` that would fail if the rule changed: playing time only (paused time
  doesn't count), live never counts, the count survives an episode change, a 3-minute manual
  pause resets it, "due within 15 minutes of the end" waits or skips, the count restarts after a break.
- Migration 12 → 13 test: existing profiles get `breakMinutes = 0`.
- Emulator, with the setting temporarily at 1 minute (a debug-only choice, not in the release list):
  film pauses with the card and resumes after 60 s; OK resumes at once; Back resumes; other keys
  don't; between episodes the next-episode card becomes the break card and then the next episode starts.
- D-pad only: the card never takes focus away from the player, Back on the card doesn't close the player.
- On the real tv (Jordy): one evening of series with 60 min. Does the moment feel right between
  episodes? Does a mid-film pause annoy?

## Open
- Is 15 minutes the right "nearly over" window for films? Easy to tune after the tv test.
- Is one minute long enough? If someone isn't back in time, they rewind with Left (10 s per press)
  or pause with OK — fine for now. If Jordy finds it too short, the fix is a break-length choice in
  Instellingen, not a "+5 min" button on the card.
