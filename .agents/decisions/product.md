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

## 2026-10-28 — Phase 1 rail without Home and Zoeken
**Decision:** The phase 1 navigation rail is Live TV · Films · Series · Favorieten; the app opens on Live TV. Home ("Verder kijken", favourite channels, recently added) and Zoeken come in phase 2.
**Why:** The spec's rail lists Home and Zoeken, but what goes on them (per-profile rows, search) is phase 2 scope. Opening on Live TV puts a channel two or three clicks away, which is a phase 1 done-criterium.
**Alternatives rejected:** Placeholder Home/Zoeken items (dead ends in the rail).
**Revisit if:** phase 2 starts; then Home becomes the start screen.

## 2026-10-28 — Minimal Instellingen screen in phase 1
**Decision:** An "Instellingen" item at the bottom of the rail with subscription info (server host:port, username; never the password), catalog status, "Catalogus vernieuwen" and "Uitloggen". Logging out wipes the whole database (catalog, favourites, resume positions) after a confirmation that defaults to "Annuleren".
**Why:** Without it the app can't change provider or refresh the catalog without clearing app data by hand, which blocks testing on the real tv. Wiping on logout because favourites and positions are tied to the old provider's ids, and viewing history shouldn't outlive the login it belongs to.
**Alternatives rejected:** Themes or other preferences (not in the spec; one accent colour by design); keeping favourites across logout (ids from another provider would point at the wrong items).
**Revisit if:** phase 2 adds profiles; then per-profile settings and PIN live here too.
