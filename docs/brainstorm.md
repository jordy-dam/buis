# Brainstorm — features and integrations

An open idea list for BUIS. Anything goes here; nothing in this file is a plan or a promise.
When an idea gets picked up it moves to a dated plan in `plans/` and its decision goes in
`.agents/decisions/product.md`. Strike it through here with a pointer, don't delete it.

**How to add:** one line under the right heading, what it does for whoever is on the couch.
Tags are optional: effort `S` (an evening) / `M` (a phase part) / `L` (a phase of its own),
and ⚠️ when the idea breaks a principle from the spec (data leaves the tv, a cloud backend, a
third-party service). Those aren't forbidden, but they need a decision before they're built.

Already parked elsewhere (don't duplicate, just pick up): number-key zapping, a Home
"Jouw diensten" row, phase 3 suggestions, the full EPG grid, hiding/reordering categories per
profile, ratings, voice search, Google TV "Watch Next".

---

## Kijken (playback)
- **Picture-in-picture for live**: keep the match small in a corner while browsing Films. `M`
- **Multiview**: two or four live channels at once (F1 onboard + main feed), audio follows focus. `L`
- **Instant replay**: Left in a live stream rewinds 10 s from the local buffer, without timeshift. `M`
- **Skip intro / recap** for series: remember per series where the intro ends after you skip it once. `M`
- **Audio and subtitle language per profile** (already noted as a "revisit" in 2e). `S`
- **Night mode**: dynamic range compression on the audio after 22:00 or per profile. `S`
- **Stream health overlay** (long-press Info): bitrate, resolution, codec, buffer, dropped frames. `S`
- **Auto-fallback**: when a channel stalls, try the same channel in another category/quality. `M`
- **Sleep timer from anywhere**, not just in the player (revisit from 2g part 2). `S`
- **Pauzemoment** → planned in `plans/2026-10-29-pauzemoment.md`. Opt-in per profile. After an hour of watching (films and episodes over an hour,
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
- **Mood/length filter** for films: "onder 100 minuten", "vanavond iets lichts". Needs per-title
  data (phase 3). `M`
- **Recently added per service**: "nieuw op Netflix-categorie deze week". `S`
- **Search history and suggestions per profile**. `S`
- **Wachtlijst voor films**: follow a film title that isn't in the catalog yet (just in the
  cinema, announced); after each catalog sync BUIS checks for it and shows "Nu te kijken" on Home.
  Same idea as Volgen, but over VOD instead of the guide. All on the tv. `M`
- **Collections**: own lists ("Kerstfilms", "Met de kids") next to favourites. `M`

## Huishouden (household)
- **Kijktijd-limiet** for the kids profile: an hour a day, then a friendly stop screen. `M`
- **Bedtime per profile** that locks the app, not just stops playback. `S`
- **Samen kijken** profile switch: watch with two profiles, history goes to both. `M`
- **Weekoverzicht**: what you watched this week, per profile, on the tv itself. `S`
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
- **Klok** in the corner of the live overlay. `S`
- **Weather/agenda strip on Home** from HA (it already knows both). `S`
- **Screensaver of your own**: posters from Verder kijken instead of Google's. `M`
- **Seasonal theming**: Sinterklaas/kerst touch on the logo, opt-in. `S`
- **Easter egg** for the BUIS name. `S`

---

## Niet doen
- **YouTube** (trailers, channels, highlights after a race), 2026-10-29. Jordy wants an in-app
  experience without trips to other apps, and YouTube's terms only allow their own player (a
  WebView, which the spec rules out). Deep-linking to the YouTube app was the only clean option.

## If I had to pick
Pauzemoment first: small, all on the tv, and it reuses the sleep timer and the next-episode card.
Then Home Assistant: you already run it, it stays in the house (no principle to break), and
Bioscoopmodus plus Volgen-notifications on your phone are small on top of what exists.
Then the phone companion page, because it fixes the one thing a remote is worst at: typing.
