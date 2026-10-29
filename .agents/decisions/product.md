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

## 2026-10-28 — Phase 2e "verfijnen": quiet focus, one step smaller, settings stay put
**Decision:** Two focus styles: artwork (posters, avatars, episodes) keeps the accent outline and glow; rows in lists (channels, categories, settings, chips, rail) get a quiet focus, a lighter surface (`SurfaceFocus`) and a thin white line at 35%, so focus never looks like the accent of "selected" and progress. Everything one step smaller: secondary text 14sp (below the spec's 16sp body, at Jordy's request), titles 16sp, rail 176/64dp, channel rows 42dp, posters 92dp, the grid 5 columns of 84dp. Home shows favourite channels as 240×60 cards. The rail starts with the avatar; the BUIS logo moves to "Wie kijkt er?" and the launcher once Jordy's round logo exists. Per-profile settings stay under Instellingen → "Voor <naam>", which now starts with "Profiel bewerken" (same PIN rules as the picker); no household defaults (a new profile copies the creator's countries). Films get genre chips from the background film details.
**Why:** Jordy found the focus border heavy and too close to the progress bar, Home showed barely one channel, and the whole UI could be a notch smaller. A "defaults" layer adds a concept nobody needs yet; what was missing was a path from Instellingen to the profile.
**Alternatives rejected:** Moving country settings into the profile edit screen (a longer path for a frequent change, behind the kids' PIN lock); one global focus style (too loud for lists or too quiet for posters).
**Revisit if:** 14sp is hard to read from the couch (one constant in `Theme.kt`), or audio/subtitle language per profile is wanted (it goes under "Voor <naam>").

## 2026-10-28 — Phase 2f "eigen gezicht": logo, avatars, one tile per film, screen stays on while playing
**Decision:** Jordy's round logo (white "B" mark over "BUIS" on blue) is the app icon, the launcher banner (the logo on its own blue, so only the mark and word show) and the header of "Wie kijkt er?" and the login screen; `docs/brand/make-logo.sh` cuts it from the source screenshot and can be re-run. Five drawn avatars (cat, fox, robot, owl, monster) as vector figures on the avatar colour, chosen under "Gezicht"; a new profile gets a face and colour nobody uses yet, a fresh household starts with the cat. Films with the same cleaned title and year are one tile ("2 versies"); the detail screen offers the versions (labelled from their tags, else country, else "Origineel") and starts the one watched last, else the one opened; search and "Recent toegevoegd" group the same way. Text one more step down, with 13sp as the floor. While video plays the screen stays on.
**Why:** Jordy's requests. The screensaver was a real bug: the TCL starts Google TV's screensaver after 10 minutes without a key press, and BUIS didn't ask to keep the screen on during playback (Media3's Compose surface doesn't do it by itself).
**Alternatives rejected:** Side-by-side banner (the stacked logo reads better and stays faithful); emoji or bitmap avatars (theme and density); merging versions by fuzzy title (wrong merges of remakes and sequels; title plus year is safe).
**Revisit if:** two different films share a title and year (then key on the provider's TMDB id if it has one), or 13sp is hard to read.

## 2026-10-29 — Phase 2g "volgen", part 1: palette, light-bar focus, vorige zender
**Decision:** Supersedes the 2e focus styles and the single light-blue accent. Background and surfaces are neutral greys (`#131416`, `#1C1D21`, `#26282D`); three colours each mean one thing: the logo's blue (`Brand`, `#2D6AA8`) for the primary button and what's selected (selected rows and tabs get a 35% tint of it), amber (`Live`) for "NU", progress bars and the channel number in the player, red (`Heart`) for the favourite heart. Focus on rows, chips, buttons and the rail is a light bar (`FocusBar`, `#ECEBE7`) with dark content and no border; `TextSecondary`, `Live` and the "Uitloggen" red switch to a darker shade on it via `onLight()`. Artwork (posters, episodes) keeps the 1.1 scale with a thin light outline and no glow; avatars keep their white ring. In the live player Left (and `KEYCODE_LAST_CHANNEL`) flips between the last two channels for the whole app run, also across categories; Up/Down then zap through that channel's own category. The overlay shows "◀ terug naar <zender>".
**Why:** Jordy: everything was too blue, favourites should look like a heart (red), the focus border wasn't pretty and a light bar is more elegant. Left/Right did nothing in the live player, and switching between F1 and a football match from another category is the common case.
**Alternatives rejected:** A warm accent for focus (focus would compete with live and progress again); a star for favourites (the app uses the heart, see 2d); an underline bar under focused posters (changes the row height; the outline is the Google TV convention); remembering the previous channel only inside one player session (loses the cross-category case).
**Revisit if:** the light bar is too bright in a dark room (one constant), or the TCL remote turns out to have a dedicated last-channel key worth labelling.

## 2026-10-29 — Phase 2g part 2: next episode by itself, sleep timer per profile
**Decision:** In the last 20 seconds of an episode (at the end when the stream gives no duration) a card counts down 10 seconds to the next episode, rolling over into the next season; OK starts it now, Back stops the countdown and lets this episode end. The next episode plays on the same player (no new screen) and resumes where that profile left it. The third episode in a row without a key press asks "Kijk je nog?" instead of counting down. The sleep timer is per profile (schema 8, `sleepMinutes`, Uit/30/60/90/120 under "Voor <naam>"), counts from the last key press in the player, warns in the last minute (any key answers, and does nothing else), then stops and closes the player.
**Why:** Jordy asked for both; per profile so a kids profile can have its own bedtime. Counting from the last key press means someone actively watching is never cut off.
**Alternatives rejected:** A household-wide sleep timer (no kids bedtime); a timer that counts from the start regardless of keys (stops someone watching); focusable buttons on the card (moving focus off the player breaks its key handling; key hints are enough on a remote).
**Revisit if:** the 20-second window cuts into real content on the provider's episodes (end credits vary), or the timer should also work outside the player.

## 2026-10-29 — 2g polish: subtle focus, smaller avatars, clearer Instellingen, pinned services
**Decision:** Supersedes the light-bar focus of 2g part 1: focus on rows, chips and buttons is one clear step lighter (`FocusFill`) with the text staying light; the primary button gets a lighter blue. Avatars in the picker are 88dp, at most five per row, more wrap into two even rows. In Instellingen changeable values are logo blue, on/off settings are switches, and each section is headed by whose it is (the profile's avatar, or the BUIS logo for "Voor iedereen"). Services in Films/Series are sorted A–Z; each profile can pin categories (long-press OK or Menu, same gesture as favourites) to a "Vastgezet" group on top.
**Why:** Jordy on the tv: the white focus bar was far too white, avatars too big next to the logo, the two settings sections looked alike, and he wants his own services (Netflix, Apple TV+) as shortcuts.
**Alternatives rejected:** A coloured focus (competes with blue = selected and amber = live); a separate pin button per row (one more focus stop on every row); a Home row of services and service suggestions now (suggestions need viewing history, which is phase 3; the pinned group already is the shortcut).
**Revisit if:** the subtle focus is hard to find from the couch in daylight, or Jordy wants the pinned services on Home too.

## 2026-10-29 — 2g part 3: vanaf begin (catch-up), pinned panel without icons
**Decision:** A live programme can be watched from its start when the channel keeps an archive (`tv_archive`, or the `⏺` name mark) and the programme started within the archive's days. Discovery is through the info overlay: the first OK shows it with "OK vanaf begin", OK while it shows plays the programme from its start through the Xtream timeshift URL (whole programme, start time in the panel's time zone); it then behaves like a film and Back returns to live. Pinned categories share one light-blue panel with a blue title, no icon or per-row bar, and no emoji in the UI.
**Why:** Jordy uses "vanaf begin" often in another app. Putting it behind the overlay keeps OK harmless for someone who only wants to see what's on. The panel alone separates "mine" from the catalog clearly enough (Jordy's call).
**Alternatives rejected:** A dedicated button on the remote (the TCL remote has no free key everyone knows); starting catch-up on the first OK (too easy to trigger by accident); guessing the tv's time zone for the timeshift start (panels count in their own zone, which can differ).
**Revisit if:** the provider's timeshift can't seek or needs a different URL form, or a guide grid arrives (then past programmes get "Terugkijken" there).
