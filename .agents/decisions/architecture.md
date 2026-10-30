# Decisions — architecture

## 2026-10-28 — Native Kotlin with Compose for TV and Media3
**Decision:** Kotlin, Jetpack Compose for TV, Media3 ExoPlayer (+ FFmpeg extension for AC3/EAC3), Room, Hilt, Coil.
**Why:** D-pad focus handling is the hardest part of a TV UI, and Compose for TV is the platform-supported way to do it. Media3 is the maintained successor of ExoPlayer. Many IPTV streams carry AC3/EAC3 audio that cheap devices can't decode without FFmpeg.
**Alternatives rejected:** Leanback (deprecated); Flutter or React Native (d-pad focus is painful, extra layer on top of the player); libVLC (LGPL obligations, weaker Android TV integration).
**Revisit if:** the target device moves away from Android TV OS (e.g. a Fire TV device running Vega OS).

## 2026-10-28 — Data stays on the device
**Decision:** Catalog, EPG, profiles, history, ratings and recommendations live in Room on the device. No cloud backend, no analytics, no third-party crash reporting. Credentials are stored encrypted (Android Keystore).
**Why:** Viewing history of household members is personal data. Keeping it on the device avoids a backend to run and secure, and avoids sending it anywhere.
**Alternatives rejected:** A small sync backend for profiles across devices; an external recommendation service.
**Revisit if:** the app needs to run on more than one tv with shared profiles.

## 2026-10-28 — Media3 pinned to the Jellyfin FFmpeg decoder version
**Decision:** Media3 1.9.0 with `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1`, renderer mode `EXTENSION_RENDERER_MODE_ON` (platform decoders first, FFmpeg as fallback).
**Why:** Google publishes no FFmpeg decoder artifact; Jellyfin's prebuilt one only exists for 1.9.0, and mixed Media3 versions break at runtime. Mode ON instead of PREFER: software-decoding every audio track costs CPU on cheap tv dongles that decode AC3 fine themselves.
**Alternatives rejected:** Building the FFmpeg extension ourselves (NDK toolchain in the build, slow, fragile); Media3 1.11 without FFmpeg (EAC3 channels silent on some devices).
**Revisit if:** Jellyfin publishes a decoder for a newer Media3, or AC3/EAC3 audio stutters on the tv (then try PREFER). Bump `media3` and `ffmpegDecoder` together.

## 2026-10-28 — OkHttp + kotlinx.serialization directly, no Retrofit
**Decision:** `XtreamClient` calls OkHttp itself and parses to `JsonElement`, mapped per item by `XtreamParser`. Big arrays are decoded as a stream (`decodeToSequence`) and written to Room in batches of 500.
**Why:** Xtream JSON is too inconsistent for typed DTOs (numbers as strings, `[]` instead of objects), and streaming 10k+ items doesn't fit Retrofit's model. Retrofit would add a layer that does nothing here.
**Alternatives rejected:** Retrofit with lenient DTOs (the spec's suggestion): one bad item fails a whole list; Ktor client (second HTTP stack next to the one Media3/Coil use).
**Revisit if:** we start talking to an API with a stable schema.

## 2026-10-28 — Credentials: DataStore + Tink AEAD, key in Android Keystore
**Decision:** Xtream login is stored as one AES-256-GCM blob (Tink) in a DataStore file; the Tink keyset is wrapped by an Android Keystore key. No HTTP logging interceptor, because every Xtream URL contains the password.
**Why:** `EncryptedSharedPreferences` (security-crypto) is deprecated; Tink is what it used underneath.
**Alternatives rejected:** EncryptedSharedPreferences (deprecated); plain DataStore (credentials in plain text on disk).
**Revisit if:** Android ships a supported replacement in Jetpack.

## 2026-10-28 — Live playback fails fast on 4xx
**Decision:** `FailFastLoadErrorPolicy`: no retries on HTTP 4xx; 5xx and network errors keep Media3's normal retries. Live tries `.m3u8` first and falls back to `.ts`; a channel whose HLS failed goes straight to `.ts` for the rest of the session.
**Why:** Measured on the emulator: with the default policy a 404 playlist was retried with 1s + 2s back-off, so a TS-only channel took 3+ seconds to start. With fail-fast it starts in ~0.6s, within the 2s zap budget.
**Alternatives rejected:** Probing which format a provider supports at login (extra requests, and providers differ per channel).
**Revisit if:** a provider returns transient 4xx (e.g. 429) where a retry would help.

## 2026-10-28 — compileSdk/targetSdk 37
**Decision:** Compile and target API 37 (Android 17); minSdk stays 28.
**Why:** The current androidx Compose/navigation/Coil releases require compileSdk 37; the spec asks to target the latest stable SDK.
**Alternatives rejected:** Older library versions to stay on 36.
**Revisit if:** something behaves differently on the tv's Android TV OS version that we can't handle.

## 2026-10-28 — Profiles: active profile is the last used one, settings live on the profile row
**Decision:** A `profile` table (Room, schema 3) holds name, avatar colour, PIN hash, preferred countries, the "alleen voorkeurslanden" switch and the kids flag. `favorite` and `playback_position` are keyed by `(profileId, type, itemId)` with `ON DELETE CASCADE`. The watching profile is simply the one with the highest `lastUsedAt`; picking a profile bumps it. `ProfileRepository` exposes it as a flow and every per-profile query goes through it (`flatMapLatest`). The 2a DataStore settings are copied once into the first profile and the file is emptied. Migration 2→3 is a hand-written `Migration` (primary keys change, so the two tables are rebuilt); existing rows go to "Profiel 1" and the catalog is emptied so the sync fills the new `category.adult` column.
**Why:** One source of truth that survives process death without extra state: a restored back stack always has a profile, and "Wie kijkt er?" can focus whoever watched last. Settings next to the profile make a switch one write, and a removed profile takes its data with it.
**Alternatives rejected:** A separate in-memory "session" plus a DataStore key for the last profile (two places to keep in sync, nothing after process death); one DataStore file per profile; an AutoMigration spec for the key change (less readable than the SQL).
**Revisit if:** profiles need to sync between devices (see "Data stays on the device").

## 2026-10-28 — Profile PIN is a household lock, not security
**Decision:** Optional 4-digit PIN per profile, stored as `salt:sha256(salt + pin)`, entered on an on-screen keypad (hardware digit keys also work). Forgotten PIN: "Uitloggen" wipes everything.
**Why:** It keeps children out of a parent's profile. Four digits can be brute-forced by anyone holding the database file, which on an unrooted tv nobody has; a slow KDF would add nothing real.
**Alternatives rejected:** PBKDF2/Argon2 (no gain at 10,000 possible PINs); a master PIN (one more thing to forget).
**Revisit if:** a profile ever protects something more sensitive than what a child may watch.

## 2026-10-28 — Full guide in Room, refreshed on app start; aliases for channels without an EPG id
**Decision:** The provider's `xmltv.php` is downloaded when the shell starts and the last refresh is older than 20 h, after every catalog sync, and on "Gids vernieuwen". It's parsed as a stream (`XmltvGuide.read`), keeping only channels in the household's preferred countries (union over profiles) and programmes from now − 2 h to now + 36 h, then swapped into Room in one transaction (`epg_programme`, `epg_channel`, `epg_info`; schema 4, AutoMigration). A stream without an EPG id borrows the id of a stream with the same cleaned name in the same country. `EpgRepository.nowAndNext` reads Room first and falls back to the short EPG.
**Why:** The short EPG per channel came back empty on the real provider; the full guide covers 98% of the Dutch channels that have an id (measured on the tv: 67 MB, 27 s). Parsing to a list and then one transaction keeps the database free for other writes during the slow download, and the screens never see half a guide. Aliases cover HD/FHD/4K duplicates, which make up much of the 55% of Dutch streams without an id.
**Alternatives rejected:** WorkManager for a nightly job (new dependency; the app only matters while it's open); keeping the whole guide (200k programmes for channels nobody here watches); matching on display names inside the XMLTV (too fuzzy across providers).
**Revisit if:** the download hurts on the tv (then refresh in the background less often or stream into Room), or coverage stays low after aliasing.

## 2026-10-28 — The tv runs the release build with R8
**Decision:** The tv gets `assembleRelease`: R8 minify plus resource shrinking, signed with the debug key (so it installs over the debug build and keeps the data). Keep rules only for what libraries can't know (`PlayerRoute.Kind`, `ContentType`, the Media3 FFmpeg classes). The emulator keeps the debug build for development.
**Why:** Measured on the tv with the debug build: 20% janky frames, p90 85 ms, p99 400 ms, almost all "slow UI thread" and one slow bitmap upload. Images and caching weren't the problem (Coil caches by default); a debuggable Compose app on a 32-bit CPU was. The release APK is 7.6 MB instead of 54 MB.
**Alternatives rejected:** A custom image cache (the measurement didn't point there); a baseline profile now (extra module; first see what R8 gives).
**Revisit if:** the tv still janks on release (then add a baseline profile), or something breaks only under R8. `run-as` no longer works on the tv, so reading its database needs a debug install.

## 2026-10-28 — Film details fetched in the background from the provider
**Decision:** `MovieEnricher` calls `get_vod_info` for every film in the household's preferred countries, 250 ms apart, paused while a player is open, resumable, failed films retried after a day, into `movie_info` (schema 5; keyed by stream id, so it survives catalog syncs). Opening a film writes its details there too. Started when the shell starts.
**Why:** The provider's film list has no genre; only `get_vod_info` has it. About 20k NL films means about 1.5 h the first time, then only new films. It stays between the tv and the provider, needs no key, and gives genre, cast and director for phase 3.
**Alternatives rejected:** TMDB now (external service, an API key, matching titles; kept as an option for phase 3 if the provider's genres prove thin); fetching on demand per category (slow chips, many bursts).
**Revisit if:** the provider rate-limits or blocks the requests (lower the pace), or the genres turn out mostly empty.

## 2026-10-29 — Code on Jordy's personal GitHub account
**Decision:** The repo lives at `git@github-personal:jordy-dam/buis.git` (private, personal account), pushed through the `github-personal` SSH alias (`~/.ssh/id_ed25519_personal`). `main` holds the current state; new work branches from `main`. Commits use Jordy's personal e-mail.
**Why:** BUIS is a personal project; it doesn't belong under the employer's GitHub account, which is what `gh` and the default `github.com` SSH host on this Mac are logged into.
**Alternatives rejected:** A private repo under the work account (mixes a personal project with work, and the work account owns it); keeping it local only (no off-machine copy of the code).
**Revisit if:** the project is shared with others or the work/personal setup on this Mac changes.

## 2026-10-29 — Catalog rows are written with INSERT OR REPLACE, not @Upsert
**Decision:** `CatalogDao.upsertLive/upsertMovies/upsertSeries` use `@Insert(onConflict = OnConflictStrategy.REPLACE)` instead of Room's `@Upsert`. Categories keep `@Upsert` (a few hundred rows).
**Why:** Measured on the tv (release, timing logs): on a filled catalog `@Upsert` tries an INSERT, catches the constraint error and then UPDATEs, per row. That cost 50 s of a 109 s daily sync for ~106k rows. With REPLACE the same write takes 6.9 s and the daily sync 67 s. Nothing references catalog rows by foreign key (favourites, positions and film details key on stream ids without FKs), so delete-and-insert loses nothing.
**Alternatives rejected:** Writing into a staging table and swapping it in (more code and a schema change for the same gain); clearing the tables before each sync (the catalog would be empty mid-sync, and a failed sync would leave nothing).
**Revisit if:** a table ever gets a foreign key to `live_channel`, `movie` or `series` (REPLACE deletes the old row, which would fire cascades), or Room's `@Upsert` stops using exceptions.

## 2026-10-30 — The guide merges two open Dutch guides with the provider's
**Decision:** On each guide refresh BUIS also downloads open-epg.com `netherlands.xml` and epgshare01 `epg_ripper_NL1.xml.gz` (`GuideSources.external`), matches their channels to our streams by EPG id (ignoring case) or by cleaned name (Dutch streams and pinned packs without a country), and merges per channel in the order open-epg → epgshare01 → provider: a later source only fills time the earlier ones leave empty. Streams without a provider id get the key `naam:<name>` in `epg_channel`, so no schema change. A source that fails is skipped; the refresh only fails when every source does. The refresh runs in quiet moments (`MainActivity.onStart`, the screensaver starting) when the guide is over 20 h old or predates today's 18:30, with an hour's pause after an attempt.
**Why:** On 2026-10-30 the provider's guide still had SBS6's old late-night schedule after a refresh, while both open guides had the change. Measured against the emulator's guide: open-epg matches 75 of the 102 Dutch provider ids, and by name the sources cover about 63 Dutch streams that had no guide at all (Viaplay TV among them). Jordy approved the merge with a preference order ("bouw maar zo"). Only the two file downloads leave the tv (IP address and user agent, no viewing data, no credentials).
**Alternatives rejected:** Showing the provider's guide and only flagging conflicts (Jordy wants the better guide on screen); a per-channel source choice now (conflicts are mostly on sports and music channels where neither source is clearly right, so it waits until conflicts are measured over days); WorkManager for idle refreshes (a tv on standby often has no network; app start and the screensaver are reliable quiet moments).
**Revisit if:** either site changes its file names, limits or terms (open-epg allows 20 downloads a day), the provider's guide turns out better for a channel group, or the per-channel conflict list gets built.
