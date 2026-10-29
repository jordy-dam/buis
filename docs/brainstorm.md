# Brainstorm — features and integrations

An open idea list for BUIS. Anything goes here; nothing in this file is a plan or a promise.
When an idea gets picked up it moves to a dated plan in `plans/` and its decision goes in
`.agents/decisions/product.md`. Strike it through here with a pointer, don't delete it.

**How to add:** one line under the right heading, what it does for whoever is on the couch.
Tags are optional: effort `S` (an evening) / `M` (a phase part) / `L` (a phase of its own),
and ⚠️ when the idea breaks a principle from the spec (data leaves the tv, a cloud backend, a
third-party service). Those aren't forbidden, but they need a decision before they're built.

✓ = Jordy wants it (2026-10-29). "Later" = yes, but not soon.

Already parked elsewhere (don't duplicate, just pick up): number-key zapping, a Home
"Jouw diensten" row, phase 3 suggestions, the full EPG grid, hiding/reordering categories per
profile, ratings, voice search, Google TV "Watch Next".

---

## Kijken (playback)
- ~~✓ **Instant replay**~~ → built (2h): in live, rewind from what the player already buffered (ExoPlayer keeps a back
  buffer, e.g. the last minute), without the provider's timeshift. Needs a key that isn't taken: Left
  is "vorige zender" in live, so maybe long-press Left or the rewind key. `M`
- ~~✓ **Skip intro / recap**~~ → built (2h) for series: the provider has no chapter data, so BUIS learns it. In the first
  minutes of an episode a quiet "Intro overslaan" hint; when you skip once, BUIS remembers where you
  landed for that series and offers the same jump on the next episodes. `M`
- ~~✓ **Geluid en ondertitels**~~ → built (2h): during a film or episode pick the audio track and subtitles from what the
  stream has (a panel in the player). Next to that a default per profile ("Nederlands, ondertitels uit"),
  which the player picks by itself when the stream has it; a choice in the player only counts for that
  title. Nothing exists yet: the player takes the stream's default today. `S`–`M`
- **Night mode**: dynamic range compression on the audio after 22:00 or per profile. `S`
- **Stream health overlay** (long-press Info): bitrate, resolution, codec, buffer, dropped frames. `S`
- ~~✓ **Zenderstatus: log hiccups, suggest a fix**~~ → built (2h; step 3, switching by itself, not). Today a live channel tries HLS, then raw TS once,
  and otherwise shows an error. Many providers carry the same channel several times ("NPO 1 HD",
  "NPO 1 FHD", "NPO 1" in another category). Idea:
  1. *Log* on the tv, per channel: errors, stalls (buffering longer than a few seconds), which source
     and quality, when. Stays on the tv like the rest of the history.
  2. *Suggest*: Instellingen → "Zenderstatus" lists channels that hiccup often, each with a proposal:
     "NPO 1 HD hapert vaak 's avonds; NPO 1 FHD liep de laatste 5 keer goed — die als standaard?"
     OK sets the replacement (favourites and zapping then use it), so nothing changes without asking.
  3. *Later*, if the proposals turn out right: switch to the sibling channel by itself when one stalls.
  `M`
- **Sleep timer from anywhere**, not just in the player (revisit from 2g part 2). `S`
- ~~**Pauzemoment**~~ → built on `feat/pauzemoment`, see `plans/2026-10-29-pauzemoment.md`. Opt-in per profile. After an hour of watching (films and episodes over an hour,
  or the running total when next-episode autoplay chains episodes), BUIS pauses for a minute and
  shows a card with three icons (wc, flesje, snack) and a countdown; OK resumes now, Back skips
  this one. Between episodes is the natural spot: when the hour is up there, the next-episode
  countdown becomes the pause card instead of pausing mid-scene. In a film there's no way to find
  a quiet moment, so it pauses where the hour falls. Reset the count after a manual pause of a
  few minutes (you already took the break). Reuses the sleep timer's per-profile setting and
  the next-episode card. `S`–`M`

## Vinden (finding)
- **"Wat is er nu op?"**: one screen with only what's live right now on your pinned channels, sorted
  by how far along it is. `S`
- **Sport hub**: everything matching your Volgen terms over the next week, grouped per sport. `M`
- ~~✓ **Mood/length filter**~~ → built (2h) for films: "onder 100 minuten", "vanavond iets lichts". Length and genre
  come from the film details BUIS already fetches in the background; mood maps onto genres
  (licht = komedie/familie/animatie, spannend = thriller/actie/horror). `M`
- **Recently added per service**: "nieuw op Netflix-categorie deze week". `S`
- ~~✓ **Kijkgeschiedenis per profiel**~~ → built (2h; search history not yet): what you watched (not only what's unfinished, like
  Verder kijken) and your recent searches, per profile. `S`
- **Wachtlijst voor films**: follow a film title that isn't in the catalog yet (just in the
  cinema, announced); after each catalog sync BUIS checks for it and shows "Nu te kijken" on Home.
  Same idea as Volgen, but over VOD instead of the guide. All on the tv. `M`
- Later: **Collections**: own lists ("Kerstfilms", "Met de kids") next to favourites. `M`

## Huishouden (household)
- **Kijktijd-limiet** for the kids profile: an hour a day, then a friendly stop screen. `M`
- ~~✓ **Bedtijd per profiel**~~ → built (2h): at a time you set (default 23:00) on the days you pick (default Mon–Thu,
  or Sun–Thu so Friday and Saturday stay free), a corner card: "Het is 23:00 · Doorkijken of lekker naar
  bed?" with **Doorkijken** (focused, nothing happens) and **Naar bed** (saves the position, stops,
  BUIS closes). Once per evening; no lock. Uses `CornerCard`, like the other pop-ups. `S`–`M`
- **Samen kijken** profile switch: watch with two profiles, history goes to both. `M`
- ~~✓ **Weekoverzicht**~~ → built (2h) per profile, on the tv: hours watched, live vs films vs series, top channels,
  films finished, longest binge, "je meest gekeken avond". Needs the watch log from the history idea
  above (time per title and channel). Maybe a Home tile on Monday. `S`–`M`
- **Guest mode**: no history, no Verder kijken, for visitors. `S`

## Integraties

### Home Assistant (you run it already)
- **Bioscoopmodus**: starting a film dims the living-room lights, pausing raises them a bit,
  the end restores them. BUIS calls an HA webhook on the local network; nothing leaves the house. `M`
- **Expose BUIS as a media player in HA**: now playing, pause, channel up/down from the HA app
  or a dashboard. A small local HTTP/WebSocket API in BUIS, or MQTT to the HA broker. `M`
- **Volgen → HA**: "F1 begint over 5 min" also as an HA notification on your phone, or a light
  that turns amber. Reuses the reminder logic. `S` once the HA link exists.
- **Presence**: nobody home for X days → pause the guide refresh; doorbell rings → pause + show the
  camera snapshot as a corner card. `M`
- **Voice via HA Assist**: "zet NPO 1 aan op de tv". `M`

### Phone as companion (local network only)
- **Phone remote / keyboard**: a tiny web page served by BUIS on the LAN for typing search terms
  and Volgen terms instead of the on-screen keyboard. Pair with a code on the tv. `M`
- **Send to tv**: share a film title from your phone (IMDb, a WhatsApp tip) to BUIS, which
  searches the catalog and opens it. `M`
- **Manage Volgen and reminders from your phone**. `S` on top of the companion page.

### Google TV / Android
- **Watch Next row** on the Google TV home screen (parked in phase 3). `M`
- **Global search**: BUIS titles appear when you search from the Google TV home screen. `M`
- **Channels on the home screen**: a BUIS row of your pinned channels on the launcher. `M`
- **Cast target**: cast a stream URL from your phone to BUIS. `L`

### Metadata and tracking ⚠️ (data or lookups leave the tv)
- **TMDB/OMDb for posters, cast, trailers and ratings** where the provider's data is thin.
  Only titles go out, no viewing history; still a third-party lookup. `M` ⚠️
- **Trakt sync**: scrobble what you watch, import your watchlist. Viewing history leaves the
  tv by design, so a deliberate per-profile opt-in. `M` ⚠️
- **Letterboxd/IMDb watchlist import** (file export, read once, nothing sent back). `S`
- **Bioscoopreleases**: a "Binnenkort in de bios" row from TMDB's NL now-playing/upcoming lists,
  ranked on the tv by the profile's genres (phase 3 weights). Only the region query goes out, never
  the profile or history. OK on a title adds it to the Wachtlijst, so you get it once it streams. `M` ⚠️

### Own media
- **Jellyfin/Plex/SMB source** next to Xtream: your own films in the same Films grid. `L`
- **Local recording** to a NAS: record a followed programme via the stream URL. Check the
  provider's terms and what's legal for private copies first. `L` ⚠️

### Backup and sync (household data, not provider data)
- **Export/import profiles, Volgen terms and history** to a file on the NAS or a USB stick,
  for a second tv or a factory reset. Was rejected once as unnecessary (Uitloggen decision);
  becomes relevant with a second tv. `M`
- **Second tv in the house**: share profiles between tvs over the LAN, no cloud. `L`

## Klein maar fijn
- ~~✓ **Klok**~~ → built (2h) in the live bar, subtle (secondary colour, small, top right of the overlay), on/off per
  profile. The switch lives in the player panel together with Geluid en ondertitels, so the player
  gets one menu for "how I watch". `S`
- **Weather/agenda strip on Home** from HA (it already knows both). `S`
- ~~✓ **Screensaver of your own**~~ → built (2h): posters from Verder kijken and favourites, slowly drifting, with the
  clock. Android TV lets an app offer a screensaver ("dream") that you pick in the tv's settings. `M`
- **Seasonal theming**: Sinterklaas/kerst touch on the logo, opt-in. `S`
- ~~✓ **Easter egg: Testbeeld**~~ → built (2h). On "Wie kijkt er?", the classic code on the remote
  (↑ ↑ ↓ ↓ ← → ← → OK) switches the screen off like an old tube tv (the picture collapses into a white
  line, then a dot), and on again into a testbeeld: colour bars with a circle, "BUIS" in the middle,
  the real time underneath and "Beste Unieke IPTV Stream" along the bottom, with a soft 1 kHz tone.
  Any key and the buis warms up again. Nothing to find in the menus. `S`

---

## Niet doen
- **Picture-in-picture and multiview** (2026-10-29): the subscription allows one stream per login,
  and both need two at once.
- **YouTube** (trailers, channels, highlights after a race), 2026-10-29. Jordy wants an in-app
  experience without trips to other apps, and YouTube's terms only allow their own player (a
  WebView, which the spec rules out). Deep-linking to the YouTube app was the only clean option.

## If I had to pick
All ✓ ideas are built (phase 2h, `plans/2026-10-29-phase-2h-brainstorm.md`); next is testing them on the tv.
Earlier note:
Pauzemoment is done (on the tv, waiting for an evening's test). Of the ✓ ideas, next:
1. **Geluid en ondertitels + klok** as one player panel: something you miss every film, and it gives
   the player one menu that later ideas (replay, intro) can hang off.
2. **Bedtijd**: small, reuses `CornerCard` and the per-profile settings pattern.
3. **Kijkgeschiedenis → weekoverzicht**: the log first, the overview is cheap on top of it.
Then Zenderstatus, intro/recap, mood/length, instant replay; Home Assistant stays a good candidate
after that.
