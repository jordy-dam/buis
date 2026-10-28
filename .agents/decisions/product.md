# Decisions — product

## 2026-10-28 — Build in three phases, test on the real tv in between
**Decision:** Phase 1 (works on my tv), phase 2 (household: profiles, history, ratings, search, EPG grid), phase 3 (on-device suggestions). Each phase ends with a test on the real Google TV before the next one starts.
**Why:** Profiles and recommendations are worthless if playback and zapping don't feel good. Playback is where IPTV apps fail, so that gets validated first.
**Alternatives rejected:** Building all features in one go from one big prompt.
**Revisit if:** phase 1 is validated and a phase 2 feature turns out to be needed earlier.

## 2026-10-28 — Personal use, sideloaded, no content shipped
**Decision:** One household, installed via adb, never published to a store. The app ships no providers, playlists or links; it plays from a subscription the user enters.
**Why:** Keeps the app a generic player. Shipping preconfigured links to unlicensed streams is itself infringement in the EU (CJEU C-527/15, *Filmspeler*).
**Alternatives rejected:** Publishing to Google Play.
**Revisit if:** there's a reason to distribute the app beyond this household.
