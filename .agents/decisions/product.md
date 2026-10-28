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

## 2026-10-28 — Phase 2 split: 2a "vinden" before 2b "huishouden"
**Decision:** Phase 2 is split. 2a "vinden" comes first: clean names, smaller cards, country grouping with a preferred country (NL) and filter, search, and an EPG coverage check. 2b "huishouden" (profiles, per-profile favourites/history, Home rows, EPG grid) follows. Phase 3 is unchanged.
**Why:** The first real-tv test (Jordy, 2026-10-28) showed finding content is the biggest gap, not profiles: the provider has 16k channels in 484 categories, 69k films and 20k series, and only ~7% of the channels are Dutch. Profiles don't help if you can't find a channel.
**Alternatives rejected:** Phase 2 as in the spec with profiles first (solves a problem one viewer doesn't have yet); per-genre search over VOD (genres only come from per-title `get_vod_info`, so 69k requests).
**Revisit if:** more household members start using the app before 2a is done.

## 2026-10-28 — Name and logo: BUIS
**Decision:** The app is called BUIS ("Beste Unieke IPTV Stream"; also Dutch slang for the telly). The logo is just the word BUIS in a plain condensed bold face (DIN Condensed Bold for the rendered banner and icon, the platform's condensed sans in the app), light on the app background. No icons, gradients or effects. Package name stays `nl.jordy.iptv` so updates install over the existing app.
**Why:** Jordy's choice; a first attempt with a play symbol in the U and a colour gradient was rejected as too busy.
**Alternatives rejected:** Play-symbol and gradient wordmark; renaming the package (would force a reinstall and lose local data).
**Revisit if:** the app is ever distributed beyond this household (DIN is only used as a rendered image, not shipped as a font).

## 2026-10-28 — Phase 2b "huishouden": profiles, Home as start screen, kids profile
**Decision:** Household profiles without login: "Wie kijkt er?" on launch when there is more than one profile (focus on the last used, so one OK), switching from an avatar at the top of the rail (two OK presses). Per profile: favourites, resume positions / "Verder kijken", preferred countries and the filter. Shared: subscription, catalog, search index, EPG. Profiles are managed in the picker ("Profielen beheren"), not in Instellingen; Instellingen shows "Voor <naam>" and "Voor iedereen". Home is now the first tab and start screen with "Verder kijken", "Favoriete zenders" and "Recent toegevoegd"; Back goes to Home, then "Afsluiten?". A kids profile hides adult categories (name contains ADULT, XXX or 18+) everywhere; once a kids profile exists, managing profiles asks a grown-up's PIN. Avatars are 8 fixed colours with the initial; no photos.
**Why:** Jordy's scope for 2b. The kids profile was a proposal: it costs one column and a filter, and without the management lock a child could simply switch it off.
**Alternatives rejected:** Profile management inside Instellingen (a kids profile could reach it); photo or emoji avatars (emoji ignore the theme); ratings, hiding/reordering categories and the EPG grid in 2b (stay in the backlog).
**Revisit if:** the household wants per-profile category hiding, or the adult name match misses the real provider's categories.

## 2026-10-28 — Phase 2c "kijken": guide in the list, compact rows, rail that steps back
**Decision:** Follows the UX review (`docs/reviews/2026-10-28-ux-review-2b.md`). Channel rows are one line of 48dp with number, logo, name, `NU · title` and a 2dp progress bar; the focused channel's time slot and "Straks" sit in a fixed strip above the list (rows don't grow on focus). The rail collapses to 72dp icons while focus is in the content, and a tab opens after focus rests 150 ms on it or at once on OK/Right. Zoeken is the first rail item and opens the keyboard on entry. Posters are 104dp with one shared vertical rhythm (`Rows` in `Theme.kt`). Home shows title and time left under the focused "Verder kijken" tile. Films and Series lose their page titles.
**Why:** Jordy found cards and sections too big on the tv and couldn't see what's on a channel. These are the review's two injunctions plus four of its orders.
**Alternatives rejected:** A row that expands on focus (the list jumps); an EPG grid now (the list with now/next covers choosing a channel; the grid stays in the backlog); service rows in Films/Series before measuring the real category names.
**Revisit if:** the 48dp rows are hard to read at Jordy's viewing distance, or the rail collapse feels jumpy on the TCL.

## 2026-10-28 — Phase 2d "bladeren": Films and Series as list + grid, genres from what the provider has
**Decision:** Films and Series use the Live TV pattern: a category list (headers "Diensten", "Genres", "Overig", grouped by name) and the whole focused category as a 4-column poster grid. Series get genre chips from the genre the catalog already carries (90% of NL series have one; English and Dutch spellings merged). Films get genres through the provider's own genre categories (Actie, Horror, Drama …) under "Genres". Channel rows: a small heart before the number, picture quality and catch-up (↺) in a fixed slot on the right, a lighter progress bar. The navigation rail stays on the left.
**Why:** Jordy asked for a whole page per service and for genre filtering. Measured on the tv: 82 NL film categories (19 services, a dozen genres) and 35 NL series categories (16 services). A list plus grid makes a service one click, uses one pattern across the app, and needs no new data. A top menu like Netflix's costs vertical space that the lists need, and the collapsed rail already gets out of the way.
**Alternatives rejected:** Fetching film genres with `get_vod_info` for 19,826 films (about 3 hours of requests to the provider; deferred to phase 3, where per-title data is needed anyway); an external metadata service now (TMDB fits phase 3, with its own decision on what leaves the device: titles only, never viewing history); a star for favourites (the app uses the heart everywhere); "see all" tiles at the end of rows (still needs scrolling to the end).
**Revisit if:** the grouping by name misses real categories, or phase 3 adds per-title genres (then films get chips too).
