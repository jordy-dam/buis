# Build prompt — Google TV IPTV app

> Gebruik: plak dit in Claude Code (Opus 5.5) in een lege projectmap, met **plan mode aan**.
> Laat het model alleen fase 1 bouwen. Test op je eigen tv. Pas daarna: "Ga door met fase 2."

---

## Role & goal

You are a senior Android TV engineer and a 10-foot UI designer. Build a personal IPTV app for
**Google TV** (Android TV OS) that talks to an **Xtream Codes API** with a username/password login.
It's for one household, sideloaded, never published. It plays content from a legitimate
subscription that I already have.

The bar: as calm and easy to use as the Google TV home screen or Netflix, not a power-user tool.
Every screen must be fully usable with the d-pad only: no touch, no pointer, no keyboard
except the login.

## Fixed stack (don't swap without asking)

- Kotlin, Jetpack **Compose for TV** (`androidx.tv:tv-material`), no Leanback
- **Media3 ExoPlayer** (+ HLS, + FFmpeg decoder extension for AC3/EAC3 audio)
- Room (local DB), Kotlin Coroutines/Flow, Retrofit or Ktor + kotlinx.serialization
- Coil for images, Hilt for DI
- Credentials stored encrypted (Android Keystore / EncryptedDataStore), never in plain text or logs
- minSdk 28, target the latest stable SDK

## Xtream API (reference)

Base: `http://{host}:{port}/player_api.php?username={u}&password={p}`

- no action → account + server info (validate login, expiry date, max connections)
- `action=get_live_categories`, `get_live_streams[&category_id=]`
- `action=get_vod_categories`, `get_vod_streams`, `get_vod_info&vod_id=`
- `action=get_series_categories`, `get_series`, `get_series_info&series_id=`
- `action=get_short_epg&stream_id=&limit=` ; full EPG: `/xmltv.php?username=&password=`

Stream URLs:
- live: `/live/{u}/{p}/{stream_id}.m3u8` (fall back to `.ts`)
- movie: `/movie/{u}/{p}/{stream_id}.{container_extension}`
- series episode: `/series/{u}/{p}/{episode_id}.{container_extension}`

Providers are inconsistent: fields can be missing, `null`, numbers as strings, broken image
URLs. Parse defensively, never crash on bad data, and show a placeholder tile instead.
Catalogs can contain 10,000+ items: sync into Room and page from Room, never render from
raw JSON. Parse XMLTV in streaming fashion (XmlPullParser), never as a whole document.

## Design principles (10-foot UI)

- Navigation rail on the left: **Home · Live TV · Films · Series · Favorieten · Zoeken**, plus the profile avatar at the top
- Large poster tiles (2:3 for films/series, 16:9 for live channels with logo + current programme)
- Clear focus state: scale ~1.1, a bright outline and a subtle shadow. Focus must never get "lost"
- Horizontal rows per category (Netflix pattern). Hero/backdrop at the top of the detail screens
- Dark theme, one accent colour, large readable type (min. 16sp body on TV), safe area of 48dp
- Back always does what you expect: player → detail → list → home → exit confirmation
- Hide categories and channels: providers ship hundreds of junk categories. The user must be able to hide and reorder them
- UI language: Dutch
- **Icons or words: weigh it every time** (2026-10-29). One icon set (Material, filled), no text glyphs
  (▶ ♥ ↺ ★ ✓: fonts draw them differently and they ignore the theme). For every new action or state, ask
  whether an icon carries it on its own:
  - **Icon** when everyone reads it without help: play/pause, 10 s back, 30 s forward, subtitles, audio,
    clock, favourite (heart), reminder (bell), follow (eye), terugkijken (history), restart, close,
    settings (gear), search, check. Quality is a badge (SD outline, HD/FHD grey, 4K/8K white).
  - **Word** for the main action of a screen or card (Afspelen, Kijken, Verder), for both answers to a
    question (Doorkijken / Naar bed), for anything you can't undo or confirm (Uitloggen, Alles wissen,
    Annuleren, Blijven), and for values (Nederlands · 5.1, 21:00, België).
  - Not sure an icon reads? Then it's a word. Never invent an icon for something new (BUIS can't record,
    so there's no record icon).
- **One place, one layout** (2026-10-29):
  - Everything that asks for a moment is a corner card (`CornerCard`): bottom right, one at a time,
    amber "what · when" line, title, a 16:9 plate on the left when there's something to show, actions
    underneath. Cards that take focus have buttons; player cards (focus stays on the picture) have key
    hints. Main action first as a word, other actions as round icon buttons or "key + icon" hints.
  - Settings-style lists (Instellingen, player Opties): icon, short title, value on the right; no
    explanatory sentence, only a status line when it changes (Bijgewerkt …).
  - Player bars: hints as icons in remote order (left action left, OK in the middle, right action right).
  - Colour means one thing each (blue selected/values, amber live/now, red heart). New meanings don't get
    a colour; use brightness, an icon or a word.

Reference apps for patterns (don't copy them):
- **TiviMate**: Live TV + EPG grid, zapping in the player (up/down = next/previous channel)
- **Google TV / Netflix**: VOD rows, detail screen, "Verder kijken"
- **Plex / Netflix**: profile picker with avatars on launch

## Phases (build ONLY the current phase, then stop and report)

### Phase 1 — Works on my tv
- Login screen (server URL, username, password), validation through the API, error states
- Sync of categories + Live/VOD/Series into Room, with a progress indicator
- Live TV: category list + channel list with logo and current programme (short EPG), playback
- Films and Series: rows per category, detail screen (cover, plot, rating, year), play.
  Series: season/episode selection
- Player: Media3, zapping up/down in Live, resume position for VOD
- Favourites (one global list for now) via long-press/menu button
- **Done when:** on a real Google TV device I can log in, find a channel within 3 clicks,
  play a film and resume it after a restart, and zapping between live channels
  takes under ~2s on a good connection

### Phase 2 — Household
- Profiles with avatar (picker on launch, optional PIN per profile)
- Per profile: favourites, watch history, resume positions, ratings (1–5 or thumbs up/down)
- Home per profile: "Verder kijken", "Favoriete zenders", "Recent toegevoegd"
- Hide/reorder categories (per profile)
- Search across Live/Films/Series with the on-screen keyboard and voice search where the platform supports it
- Full EPG grid (time × channel) for Live

### Phase 3 — Suggestions
- On-device recommendations, **no external service, no data off the device**:
  build genre/actor weights from history + ratings per profile (Xtream `get_vod_info` / `get_series_info`
  provide genre, cast, rating), score items not yet watched, rows like
  "Omdat je *X* keek" and "Aanbevolen voor *profiel*"
- Optional: Google TV "Watch Next"/Continue Watching integration
- Explain in a short note how the score is calculated, so I can tune it

## How you work

1. First: a short plan (architecture, packages, data model, screen flow). Wait for my OK.
2. Build a **mock Xtream server** (a local fixture or MockWebServer with realistic, messy JSON)
   so development and tests don't depend on my provider. Never put real credentials in the repo.
3. Tests that matter: parsing messy/missing fields, sync of a large catalog,
   the resume-position logic, the recommendation score (phase 3). No tests that just mirror the code.
4. Verify on the Android TV emulator (1080p) **and** describe how I install it on my tv with
   `adb install`. Check focus navigation on every screen with the d-pad only.
5. End each phase with: what works, what was tested and how, known limitations, and what I
   need to test on the real tv.

## What I don't want

- No Leanback, no WebView UI, no cross-platform framework
- No analytics, tracking, crash reporting to third parties, no cloud backend
- No preconfigured providers, playlists or links in the app
- Don't add features that aren't in the current phase
