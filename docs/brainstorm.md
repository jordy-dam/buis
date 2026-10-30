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
- ~~**Slanke spelerbalk**~~ → built 2026-10-30 (5e6fa8d), outside this list: no panel but a fade, one
  title line, times above a thin line. One key model: without buttons the arrows steer the picture,
  OK brings the buttons and then only Back leaves. Films scrub on the progress line.
- ~~**Automatisch overschakelen**~~ → built 2026-10-30 (508a8eb): per profile, a followed programme that
  starts counts down 10 s on its reminder and tunes in by itself; Later or Back stops it.
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
- ~~**Profielpagina met gadgets**~~ → built 2026-10-30 (5bb9b2b): face, colour and gadget each from a
  button in the name row (pop-up on black); 12 faces, 12 colours, 16 gadgets (wrench, football,
  doctor, mouse…). PIN/kids mark upper left, gadget lower right, also in "Wie kijkt er?".

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
- ~~✓ **Tweede gidsbron**~~ → built (7776bf5, 2026-10-30: open-epg → epgshare01 → provider, merged per
  time slot): a public XMLTV for Dutch channels next to the provider's. BUIS compares
  the two per channel and shows conflicts in Instellingen ("SBS6: provider zegt De Bondgenoten,
  bron 2 zegt True Crime"); when one source keeps being right for a channel, OK swaps which one
  leads there. Prompted by SBS6 on 2026-10-30, where the provider missed a late-night schedule
  change even after a refresh. Only the guide download goes out, no viewing data. `M` ⚠️
  *Checked 2026-10-30, against the emulator's guide:*
  - **Source:** open-epg.com `files/netherlands.xml` (4 MB, 292 channels, ~2 days ahead). It had
    SBS6 right (True Crime Today 23:56, De Bondgenoten 00:29). Free, donation-backed, updated once
    a day around 18:00 CET, max 20 downloads a day: so one download a day, after 18:00. epgshare01
    `epg_ripper_NL1.xml.gz` (18 MB, 4 days) was just as right and is the backup; globetvapp is stale.
  - **Matching:** open-epg uses the same ids as the provider, differently cased (`SBS6.nl` vs
    `sbs6.nl`): lowercased, 75 of the 102 Dutch provider ids match, all big channels included.
    epgshare01 adds 7 more after stripping punctuation/HD. Missing: mostly Ziggo Sport sub-channels,
    regional channels, BBC/Fox/NatGeo.
  - **Conflicts:** over the next 24 h the two disagree on ~11% of time slots, nearly all on sports
    (Eurosport, Ziggo Sport, ESPN) and music channels, where both look plausible. So report
    conflicts per channel only when they repeat, not every time slot, or Instellingen fills up.
  - **New channels:** of the 651 Dutch streams without any guide today, the three sources together
    know 32 names (63 streams) by display name, among them Viaplay TV (7 streams, the old Viaplay F1
    gap), FilmBox, Love Nature, DreamWorks, Moonbug, BVN, ZDF/WDR/NDR, Arte and some regionals.
    Most of the rest are divider rows, ESPN Watch event feeds and Rakuten channels no source has.
  - **Shape (Jordy, 2026-10-30):** merge all sources into one guide with a preference order:
    per channel and time slot the first source that has data wins, the others fill gaps. Default
    order open-epg → epgshare01 → provider; a channel can get its own order from the conflict list.
    Display-name matching (not just ids) is what brings in the new channels.
- **Gidsconflicten per zender** (the rest of the Tweede gidsbron): Instellingen → Catalogus en gids lists
  channels where the sources keep disagreeing, and OK picks which source leads there. `S`–`M`

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
- ~~**Instellingen in secties**~~ → built 2026-10-30 (8fa782a): two panes like the tv's own settings,
  each section with a one-line summary of how it's set. Pop-ups (cards, programme info) lost their
  boxes for the bar's soft fade (cfb7791).
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
*2026-10-30:* all ✓ ideas are built, plus the player, settings and profile rounds above (on main,
emulator only). First: everything on the tv and an evening's use. After that, the cheapest wins:
1. **Gidsconflicten per zender**: finishes the second guide source; the data is already there.
2. **"Wat is er nu op?"**: one screen, all data already on the tv.
3. **Sleep timer from anywhere** and **night mode**: small, every evening.
Then Home Assistant (bioscoopmodus) as the first integration.

*Earlier (2026-10-29):* all ✓ ideas built (phase 2h, `plans/2026-10-29-phase-2h-brainstorm.md`).
Earlier note:
Pauzemoment is done (on the tv, waiting for an evening's test). Of the ✓ ideas, next:
1. **Geluid en ondertitels + klok** as one player panel: something you miss every film, and it gives
   the player one menu that later ideas (replay, intro) can hang off.
2. **Bedtijd**: small, reuses `CornerCard` and the per-profile settings pattern.
3. **Kijkgeschiedenis → weekoverzicht**: the log first, the overview is cheap on top of it.
Then Zenderstatus, intro/recap, mood/length, instant replay; Home Assistant stays a good candidate
after that.
