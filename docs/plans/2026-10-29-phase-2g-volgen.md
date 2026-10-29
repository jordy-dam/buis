# 2026-10-29 — Phase 2g: volgen

Status: **part 1 (colours, light-bar focus, vorige zender) built and on the tv (2026-10-29).
Part 2 (volgende aflevering, slaaptimer) built and emulator-tested; not on the tv. Parts 3–4 not built.** Branch `feat/phase-2a`. Decision: `.agents/decisions/product.md` (2g part 1).

## Why
Jordy's feedback on 2f on the tv: duplicate films are better but the version choice is clunky and
too long; everything is very blue; he wants to be told when F1 (Viaplay) or FC Utrecht (ESPN)
starts while he's using the app, and wants a quick way back to the previous channel. Both sport
channels are in the Ziggo cable list, which has a guide.

## What to build

### 1. Vorige zender
- Live player: **Left** jumps to the previous channel, a second Left goes back again (toggle).
  `KEYCODE_LAST_CHANNEL` does the same for remotes that have it. Left/Right do nothing in live now.
- The overlay briefly shows "← <previous channel>" so the toggle is discoverable.
- The previous channel may sit in another category: remember the stream id and its category, not an index.

### 2. Volgen + herinnering
- **Follow a search term per profile** ("Formule 1", "FC Utrecht"), optionally tied to one channel.
  Start points: "Herinner me" on a programme in the guide/"Straks" strip (offers to follow its
  title), and a "Volgen" list in Instellingen → "Voor <naam>".
- Matching runs over `epg_programme` after every guide refresh, case- and accent-insensitive on the title.
  This also catches races the 36-hour guide window doesn't show yet when you add the term.
- **In-app banner 5 minutes before the start**, wherever you are in the app (also in the player on
  another channel): "Race begint over 5 min · Viaplay F1" with **Kijken** (focused) and **Later**.
  A second banner at the start time if ignored. No system notifications: only while the app is open.
- **Home row "Straks voor jou"** with the next matches of what you follow (today and tomorrow).

### 3. Versiekeuze netter
- The row of long chips becomes **one button** next to Afspelen: "Nederlands gesproken · 3 versies ▾".
- OK opens a short list; each version is two lines: language in plain Dutch on top
  ("Nederlands gesproken", "Engels, NL ondertiteld", "Engels"), title and quality small underneath
  ("The Croods: A New Age · 4K"). The title only shows when the titles differ.
- Names come from the badges: `NL gesproken` → "Nederlands gesproken", `NL sub` → "<origineel>, NL
  ondertiteld", none → "Origineel"; quality (4K, HDR) moves to the second line.

### 4. Kleuren
- Background and surfaces neutral (no blue tint): `#131416`, `#1C1D21`, `#26282D`, focus `#31343A`.
- **Logo blue `#2D6AA8`** for the primary button and the selected tab/chip.
- **Amber `#F2B544`** for "NU", progress bars and reminders.
- **Red `#EF5B5B`** for the heart, everywhere.
- **Focus becomes a light bar, no border**: the focused row, chip or button fills with `#ECEBE7`
  and its text turns dark (`#131416`, secondary `#5C5F66`); on it the heart is `#D63C3C` and "NU" a
  dark amber `#8A5A00` so both keep their contrast. Posters and cards have no fill to change: they
  scale up as now and get a 3dp light underline bar instead of the outline. Replaces the white line from 2e.
- Banner background follows the logo blue.

### 5. Volgende aflevering automatisch
- In the last 20 seconds of an episode (or at the end credits when the provider gives no
  duration, at the end), a corner card: "Volgende: S2 · A5 <title> over 10 s" with **Nu kijken**
  and **Stoppen**. No key press → the next episode starts; it rolls over to the next season.
- Stops after three episodes in a row without a key press ("Kijk je nog?"), so a sleeping household
  doesn't play a whole season.
- Not after the last episode of the last season.

### 6. Slaaptimer
- Instellingen → "Voor <naam>" → **Slaaptimer**: Uit, 30, 60, 90, 120 min (per profile, so a kids
  profile can have its own bedtime).
- Counts from the start of playback and restarts on every key press. One minute before the end a
  banner "Nog aan het kijken?" (any key answers it); no answer → playback stops, the player closes
  back to where it was opened and the screen may go to the screensaver (keep-screen-on is released).

### 7. Vanaf begin kijken (catch-up)
- Store `tv_archive` and `tv_archive_duration` from `get_live_streams` (schema 8); the `⏺` name mark
  stays as a fallback.
- On a channel with archive: in the live player, **OK** shows the info overlay with **Vanaf begin**
  for the current programme; in the guide, programmes from the past archive days get **Terugkijken**.
- Plays through the Xtream timeshift URL
  (`/timeshift/<user>/<pass>/<minutes>/<YYYY-MM-DD:HH-MM>/<stream>.ts`) with normal seek controls;
  "Live" in the overlay jumps back to the live stream.
- The reminder banner gets **Vanaf begin** next to Kijken when you open it after the start.

### Later (not in 2g)
- Zender kiezen met de cijfertoetsen: useful but rarely used.

## Done when
- Left in the live player toggles between the last two channels, across categories, in under one zap time.
- A followed term matching a programme 5 minutes from now shows the banner on Home, in Films and in
  the player; Kijken starts that channel; Later hides it until the start.
- A film with 3 versions shows one button; the list reads as languages, not provider tags.
- No light blue left on screen; hearts are red; focus is the light bar everywhere (rows, chips,
  buttons, rail, Instellingen) and readable from the couch.
- An episode ends → the next one starts after 10 s, including across a season; after three unattended
  episodes it asks first.
- Slaaptimer of 30 min stops playback after 30 min without a key press, with the warning one minute before.
- On a channel with archive, Vanaf begin starts the running programme at its start and Live returns.
- Unit tests: term matching (case, accents, "F1" vs "Formule 1" stays literal), banner timing
  (5 min, at start, not again after Kijken), the previous-channel toggle, version names, next
  episode across seasons and the three-episode stop, sleep timer reset on a key press, the
  timeshift URL (start time in the provider's time zone, duration rounded up).

## Open
- Does the Ziggo guide say "Formule 1: Grand Prix …" or something generic such as "Live sport"? First
  check: follow "Formule 1" on the tv and look at "Straks voor jou".
- Do Viaplay F1 and ESPN have `tv_archive`, and how many days? Jordy uses "vanaf begin" often in
  another app, so the subscription supports it; which channels do is checked on the first sync.
- Order of building: colours + focus and vorige zender first (small, visible at once), then volgende
  aflevering and slaaptimer, then catch-up, then volgen. Each part goes on the tv on its own.

## Part 1 — built (2026-10-29)
- `Theme.kt`: new palette, `onLight()`, content-aware `TextSecondary` and `Live`; `Focus.bar()` in `Tiles.kt` for every row, chip, button and rail item.
- `ChannelHistory` (app-wide, last two channels) + `PlayerViewModel.previousChannel()`; Left in the live player.
- Tested: unit 98 green (new `ChannelHistoryTest`); emulator walk: rail, category list, channel rows (dark amber "NU"
  and red heart on the focus bar, checked by pixel), film detail buttons, Left back and forth inside a category and
  across two categories with Up/Down zapping the restored list. Release build made, not installed.
- Not done from the plan's "Done when": Instellingen and the profile editor only looked at in code, not on screen.
- On the tv (release): Home shows the light bar and amber progress. Seen there: the "VIAPLAY FORMULE 1"
  favourite card shows no programme, so that channel lacks guide data from the provider's EPG; part 4
  (volgen) must find F1 through the Ziggo cable channel or a channel alias.

## Part 2 — built (2026-10-29)
- `Autoplay` (next episode across seasons, card timing, ask after three unattended) and `SleepTimer`
  (warning in the last minute, reset on a key), both pure and unit-tested.
- Player: the next episode swaps the media item on the same player; resume positions follow the
  episode that plays (`vodId`). Card bottom right: "Volgende aflevering over N s", OK = nu kijken,
  Terug = stoppen (this episode just ends). Sleep warning top centre; the key that answers it does nothing else.
- Schema 8: `profile.sleepMinutes` (auto-migration, 0 = off), Instellingen → "Voor <naam>" → Slaaptimer
  cycles Uit / 30 / 60 / 90 / 120.
- Tested: unit 109 green (Autoplay, SleepTimer, migration 7→8). Emulator: S1 A1 → card counts down →
  S2 A1 starts by itself (next season); Terug on the card stops the countdown and nothing starts; no card
  after the last episode; S2 A1 resumes where it was left; Slaaptimer row in Instellingen goes Uit → 30 min.
- Not seen on screen: the sleep warning and the stop itself (30 minutes); covered by `SleepTimerTest` only.
  The "Kijk je nog?" variant after three episodes is unit-tested, not walked.

## Polish from Jordy's tv feedback — built (2026-10-29)
- **Focus**: the white bar was too bright; rows, chips and buttons now step up to `FocusFill` (#3B3E46)
  with light text, the primary button to a lighter blue. Posters keep the thin light outline.
- **Avatars** in "Wie kijkt er?": 88dp (smaller than the 96dp logo), up to five in a row, more wrap into
  two even rows (`FlowRow`, 6 → 3 + 3, 9 → 5 + 4).
- **Instellingen**: values you can change in logo blue (`BrandText`), on/off as a switch, sections headed by
  the profile's avatar ("Voor Bas") and the BUIS logo ("Voor iedereen", with one line on what's shared).
- **Diensten A–Z** and **pinning per profile** (schema 9, `profile.pinnedCategories`): long-press OK or Menu
  on any category in Films/Series pins it under "Vastgezet" at the top (A–Z), again to unpin; films and
  series pins are separate. Focus follows the category when it moves.
- Tested: unit 113 green (grouping A–Z and pins, migration 8→9). Emulator: picker, Instellingen (focus,
  blue values, switch, headers), pinning Drama and Thriller incl. after a restart.
- Not done: a "Jouw diensten" row on Home and suggesting a service (that needs viewing history → phase 3);
  six or more profiles not walked on screen (layout rule unit-free, by reading).
- On the tv (release, 2026-10-29): starts without errors, schema 7 → 9 migrated; profile, "Verder kijken" and favourite channels intact; the grey focus shows on Home.

## Vastgezet panel (2026-10-29)
Pinned categories sit together on one light-blue panel (`Brand` at 16%) with a blue "VASTGEZET" title;
no icon and no bar per row (Jordy: the panel is enough, no emoji anywhere). The panel is one list item,
so the focus-follow after pinning scrolls to it.

## Part 3 — vanaf begin, built (2026-10-29)
- `tv_archive` / `tv_archive_duration` → `live_channel.archiveDays` (schema 10, catalog re-syncs once);
  `hasArchive` also counts the old `⏺` name mark, and the ↺ in channel rows and search now follows it.
- Live player: OK shows the info with "OK vanaf begin" when the running programme is in the archive
  (`Catchup.canRestart`); OK again plays it via `StreamUrls.timeshift` (whole programme, minutes rounded
  up, start in the panel's time zone from `server_info.timezone`, asked once per player). It then
  behaves like a film (OK pause, ◀ 10 s, 30 s ▶); Back returns to live; Up/Down/Left still zap.
- Tested: unit 123 green (timeshift URL across time zones, archive parsing, `Catchup`, migration 9→10).
  Emulator against the mock server (which now has archive channels and a `/timeshift/` route): ↺ on
  archive channels, the hint, playback from 10:30 with the request
  `timeshift/…/30/2026-10-29:10-30/1020.ts`, Back to live.
- Not built from the plan: "Terugkijken" on past programmes in a guide (BUIS has no guide grid yet) and
  "Vanaf begin" on the reminder banner (part 4). Not known yet: whether Jordy's panel allows seeking
  inside a timeshift stream; the real-tv test will show it.
- Installed on the tv (release, 2026-10-29 10:44): install succeeded and the app process started; the screen was not checked because the tv sat in its idle screensaver with the auto-standby countdown. First open re-syncs the catalog.

## Uit Verder kijken (2026-10-29)
Long-press OK or Menu on a "Verder kijken" tile asks "Uit Verder kijken halen?" (focus on Verwijderen);
it forgets that profile's position, so a later play starts at the beginning. Focus moves to the next
tile (or the previous one). Tested: unit 124 (removal is per profile); emulator: dialog, removal of the
only tile, row disappears and focus lands on the rail. Moving focus to a neighbour tile not walked.
- On the tv (release, 2026-10-29): starts, catalog re-synced, Verder kijken and favourite channels shown. "RTL 7 8K+" no longer appears among the favourite channels on Home (it did this morning); Jordy removed it himself; not a bug.

## Fixes after Jordy's tv test (2026-10-29)
- **Long-press opened and confirmed the remove dialog at once**: the held OK's repeats and release landed
  on the focused Verwijderen. `Modifier.ignoreHeldKey()` swallows them until a fresh key press; also used
  on the reminder dialog. Emulator: `input keyevent --longpress DPAD_CENTER` leaves the dialog open.
- **Version choice** is now one button next to Afspelen ("Nederlands gesproken · 3 versies ▾") opening a
  list, two lines per version: the language in plain Dutch, then title (only when titles differ) and quality.
- **"(NL Subs)" and friends** leave film titles: NL SUBS / SUBBED / SUBTITLED / GESUBD / ONDERTITELD /
  DUTCH SUBS → badge "NL sub"; NL DUB / DUBBED → "NL gesproken". Titles change at the next catalog sync.

## Part 4 — volgen, built (2026-10-29)
- Schema 11: `follow` (profile, term). Instellingen → "Voor <naam>" → Volgen: type a term, OK on a term removes it.
- `FollowDao.matches` searches the guide titles (case-insensitive LIKE) through `epg_channel` to every live
  channel; `FollowMatches.fold` keeps one channel per programme (the one with an archive first); a kids
  profile never gets adult channels.
- Home "Straks voor jou": the next matches (NU in amber when running), OK tunes in.
- Reminder dialog over everything (also a playing film), checked every 20 s: "Over 4 min" five minutes
  before; Later → again at the start ("Nu begonnen", with Vanaf begin when the channel has an archive);
  Kijken replaces a playing player with that channel. Not over login, sync or the profile picker.
- Tested: unit 134 green, run twice (matching, fold, timing, kids filter, migration 10→11; plus a fix so
  a closed test database no longer leaks an error into the next test class). Emulator with the clock set
  to 11:26 and 11:30: "Over 4 min · Sport 101 · NPO 1", Later, "Nu begonnen", Kijken plays NPO 1.
- Not done: following a programme straight from the guide/player (terms are typed in Instellingen), and
  tying a term to one channel. Whether F1 shows up depends on how the Ziggo guide titles it.
- Installed on the tv (release, 2026-10-29 11:23): install succeeded, app process started, no crash in the log; the screen was in the Google TV screensaver, so not seen.

## Volgen after Jordy's first use (2026-10-29)
- "Zappelin" filled "Straks voor jou" with every short NPO 3 programme. Back-to-back matches on one channel
  now fold into one block (first start → last stop), and Home shows the first block per term only; reminders
  come once per block.
- "FC Utrecht" found nothing for tonight's cup match: matching now also searches the programme description
  (cup matches are often titled "KNVB Beker" with the clubs in the description). Whether that's enough for
  Jordy's guide shows in the Volgen dialog, which now lists per term what it finds next (or "Nog niets in de gids").
- How far ahead: as far as the guide reaches, 36 hours from its last refresh (refreshed when older than 20 h).
- The reminder is a card in the bottom-right corner with the channel logo and the start time, over a lightly
  dimmed screen, instead of a dialog in the middle.
- Tested: unit 137 green (blocks, one per term, description match). Emulator: the card, one Home tile per
  term, the Volgen dialog's "11:30 · NPO 1 · Sport 101".

## Guide per channel, pinned live categories, guide coverage (2026-10-29)
- **Right on a channel** opens its guide on the right: NU, then everything coming (stored guide, else the
  provider's short EPG). The focused programme shows description and times. OK on NU tunes in; OK on a later
  programme sets a reminder for exactly that title (OK again removes it); "Herinnering staat aan" marks it.
- **Live TV categories can be pinned** like services (long-press OK / Menu): "Ziggo Kabel", "Canal+" on the
  light-blue panel on top.
- **The stored guide now also covers pinned live categories and favourite channels** of every profile, not
  only the preferred countries. Likely why "FC Utrecht" found nothing: sports channels in a category without
  a country tag weren't in the guide, so following couldn't see them. Pinning a live category refreshes the
  guide at once; a new favourite joins at the next refresh (or Instellingen → Gids vernieuwen).
- Reminder card: "Volgen aanpassen" opens the terms right there. Volgen dialog: Toevoegen on top, terms below.
- Tested: unit 138 green (guide coverage query). Emulator: pinning "Events · PPV", the guide panel with
  descriptions and the reminder mark, OK sets a reminder, the card with Volgen aanpassen and the dialog.

## Text one step smaller, on the tv (2026-10-29)
All type styles one step down (bodyMedium/labelLarge 12sp, titleMedium 14sp, headline 20sp) as a tv test
at Jordy's request; 12sp is the new floor. One place: `typography` in `Theme.kt`.
On the tv right after installing, Home showed "Straks voor jou" with real matches: "15:00 · ZIGGO SPORT 3 ·
The Inside Line" and "17:30 · ESPN 2 · Eurojackpot KNVB Beker" (FC Utrecht found through the description).

## One-off reminders (2026-10-29)
OK on a later programme in a channel's guide set a Volgen term for its whole title, so every airing of
e.g. "ESPN Vandaag" lit up. Now OK sets a reminder for that one programme (table `reminder`, schema 12:
profile, channel, start), OK again removes it; long-press follows the title as before ("Je volgt dit").
One-off reminders join "Straks voor jou" (each one shows) and the same corner card, and are deleted once the
programme is over. The reminder's "Volgen aanpassen" dialog now has its terms loaded before it opens.
Tested: unit 142 (one programme only, toggle off, cleanup, merge, migration 11→12). Emulator: OK on
12:30 Sport 1035 marks only that one, long-press on Journaal 1035 marks every Journaal 1035.
Text size: Jordy keeps the smaller step ("perfect zo").
- On the tv (release, 2026-10-29 ~12:20): starts, schema 12 migrated, terms and Home intact ("Straks voor jou" shows SBS 6, Ziggo Sport 3, ESPN 2). Titles followed with the old guide OK (e.g. "De Bondgenoten") stay as Volgen terms until removed in the Volgen dialog.

## Uitloggen keeps the household (2026-10-29)
`SubscriptionData`: logout clears only the catalog and guide tables; a login on a different server (host:port,
remembered at logout/login) also clears favourites, positions, one-off reminders, pins and film details.
Tested: unit 145 (logout keeps profile/favourites/position/genres; same panel new account keeps all; other
provider clears id data, keeps profiles and terms). Emulator: Uitloggen dialog with the new text, log out,
log in again with the mock account → Bas, Verder kijken, Straks voor jou and favourites all still there.
- Follow-up: the logout confirmation is a ✓ (stays) / ✕ (goes) list instead of a paragraph. New row
  "Alles wissen" under Uitloggen: the same kind of list, all ✕, focus on Annuleren, guarded by a grown-up
  PIN once a kids profile exists (the profile-management rule); wipes everything incl. the remembered server.
  Tested: unit 146; emulator: the logout list, the PIN gate in front of Alles wissen (the wipe list itself
  not opened there: it needs the test profile's PIN; same composable as the logout list).
