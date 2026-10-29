# Handoff — feature work

---
session_date: 2026-10-29
tool: Claude Code
session_focus: Brainstorm list, then Pauzemoment and one card style for all pop-ups
---

## TL;DR for the next person
BUIS runs on the real tv (TCL Smart TV Pro, Android 14) with Jordy's subscription, as a release build.
Everything through phase 2g is on the tv and Jordy is happy with it. The code is on GitHub:
`git@github-personal:jordy-dam/buis.git` (private, personal account), `main` = current state (776e166).
Plan and per-part notes: `docs/plans/2026-10-29-phase-2g-volgen.md`. Decisions: `.agents/decisions/product.md`.
New since: `docs/brainstorm.md` (open idea list) and **Pauzemoment** on branch `feat/pauzemoment`
(plan `docs/plans/2026-10-29-pauzemoment.md`): built, emulator-tested and on the tv since 13:24 (release, 6acd0b4). Pauzeduur setting (schema 14) and the autoplay-title fix on the tv since 13:46 (release, 29cef3d). Branch not merged, not pushed.

## State
**Where we are:**
- On the tv (schema 12): neutral palette (logo blue = selected/values, amber = live, red = heart), subtle grey
  focus, text one step smaller (kept, 12sp floor — never smaller); Left = vorige zender; next episode by itself;
  sleep timer per profile; vanaf begin via Xtream timeshift; pinned categories (Films, Series, Live TV) on a
  light-blue "Vastgezet" panel; Uit Verder kijken (long-press); version list behind one button.
- Volgen: terms per profile (Instellingen → Volgen, also from the reminder card), title + description match,
  back-to-back matches folded into one block, "Straks voor jou" on Home (one tile per term + every one-off),
  corner reminder card 5 min before and at the start. Guide per channel: Right on a channel row; OK on a later
  programme = one-off reminder (table `reminder`), long-press = follow the title.
- The stored guide covers preferred countries + every profile's pinned live categories + favourite channels.
- Uitloggen keeps profiles, history, terms and film details (`SubscriptionData`); another server clears only
  provider-id data. "Alles wissen" wipes everything behind a grown-up PIN.

- On `feat/pauzemoment` (schema 13): Pauzemoment per profile, and the reminder, next-episode, break and
  sleep-timer cards share `CornerCard` (bottom right, one at a time in the player).

- Phase 2h on the same branch (schema 20): Opties in the player, Bedtijd, Kijkgeschiedenis + weekoverzicht,
  Zenderstatus, Intro overslaan, Stemming/lengte, instant replay, Testbeeld easter egg, round PIN keys,
  screensaver. Plan: `docs/plans/2026-10-29-phase-2h-brainstorm.md`. Emulator-tested; on the tv since 16:11 (release fee9212, incl. icons, shorter Instellingen, Opties fix, Versie row); screensaver not switched on there.

**Last completed step:** consistency round built (f8f255f): HD/SD badges (schema 21: the first launch re-syncs
the catalog), icon buttons on cards and detail, no text glyphs left, and the icon/word + card-layout rule in the
spec's design principles. Release built, **not on the tv yet** (the tv has fee9212).

**Mid-flight:** nothing — clean break. Jordy still has to set `main` as default branch on github.com.

## Next up
1. Jordy tests 2h on the tv. The screensaver needs his OK for `adb shell settings put secure screensaver_components nl.jordy.iptv/.ui.dream.BuisDream`. Then, then merge `feat/pauzemoment` to `main` and push. Ask Jordy: does a mid-film pause annoy, is the corner card style right for all four cards?
2. Start other new work from `main` on a fresh branch. Ideas: `docs/brainstorm.md` (next pick: Home Assistant).
3. Wait for Jordy's report on vanaf begin on real channels (↺ on Viaplay F1/ESPN? seeking in timeshift?) — fix only if needed.
4. Candidates when he asks: number-key zapping (parked), auto-play into a Home "Jouw diensten" row,
   phase 3 suggestions (service suggestions belong there), a full EPG grid.

## Open questions
- Does Jordy's panel allow seeking inside a timeshift stream?
- Old guide-OK title follows (e.g. "De Bondgenoten") are Volgen terms Jordy may want to remove.
- BUIS icon not showing on the Google TV home (2026-10-29): the release APK has icon + banner and the tv
  lists BUIS as a leanback app, so likely a stale launcher image (BUIS was first installed before the logo).
  Need Jordy's description (blank tile, old icon, or missing) before changing anything.

## Don't forget
- Every `adb install` stops the running app on the tv: only install when Jordy says so; always `install -r`
  (an uninstall would wipe the household). Release build: `./gradlew :app:assembleRelease`, then
  `adb -s 192.168.68.91:5555 install -r app/build/outputs/apk/release/app-release.apk`. adb lives in
  `/opt/homebrew/share/android-commandlinetools/platform-tools/`. Release isn't debuggable (no `run-as`).
- The tv often sits in its screensaver when installing: the app starts but the screen can't be checked.
- Room schema JSON is written during the build; a new migration test may need a second test run.
- gh CLI is logged into the work account; push to the personal repo via the `github-personal` SSH alias.

## Emulator gotchas
- Clock drifts after a suspend (mock guide then has no "NU"): `adb -s emulator-5554 shell cmd alarm set-time $(($(date +%s)*1000))`.
  Setting it to e.g. hh:26 is how reminders were tested.
- Google TV may show "Set up Google TV" on top: Back, relaunch. Profile picker first: OK on Bas before navigating.
- Screenshots: `adb shell screencap -p /sdcard/s.png` + `adb pull` is reliable; `exec-out` can hang.
- Mock server: `./gradlew :mockserver:run` (login demo/demo at 10.0.2.2:8080); has archive channels and
  `/timeshift/`; restart by killing only the listener (`lsof -ti tcp:8080 -sTCP:LISTEN`).

## How to run locally
See `.agents/onboarding/new-dev.md` (mock media: `./mockserver/generate-media.sh`).

## Who to ask
- Jordy: owner, tests on the real Google TV.
