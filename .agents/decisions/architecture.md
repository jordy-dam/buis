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
