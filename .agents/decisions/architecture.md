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
