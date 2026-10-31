# Handoff — feature work

---
session_date: 2026-10-31
tool: Claude Code
session_focus: Uiterlijk per profile (Standaard + Garage), then everything on main as a release build on the tv
---

## TL;DR for the next person
BUIS runs on the real tv (TCL Smart TV Pro, Android 14) with Jordy's subscription, as a release build.
Since 2026-10-31 19:56 the tv runs a release of `main` at **2b8ab3a**. That includes own guides,
Agenda's, Uiterlijk/Garage and the new reminder cards. Code: `git@github-personal:jordy-dam/buis.git` (private).
**main is 6 commits ahead of origin, not pushed** (6e15a27 is the last pushed). Decisions: `.agents/decisions/`.

## State
**Where we are:**
- **Uiterlijk (4072189):** Instellingen → Voor <naam> → Profiel → Uiterlijk cycles Standaard / Garage, per
  profile (schema 27, column `profile.look`). All colours come from one `Palette` per `Look` in
  `ui/theme/Theme.kt`; the named colours (`Brand`, `Live`, `Heart`…) are `@Composable` getters on the active
  palette, so screens didn't change. `MainActivity` feeds the current profile's look into `IptvTheme(look)`;
  the screensaver (`BuisDream`) always uses Standaard. Garage = near-black base, red accent, gold live,
  white heart. Colours only: no textures yet. Emulator-checked (Bas is on Garage there); `LookTest` green.
- **Reminder cards (2b8ab3a, from a parallel session):** the cards use the player bar's fade, slide in, hide
  after 15 s, and refusing one means it doesn't come back at the start. Decision in `product.md`.
- Earlier on main and on the tv: own guides (ac59f38, 4dc9e06), Agenda's (e91e71c), fixed player row (6e15a27),
  merged guide (7776bf5).

**Last completed step:** release of 2b8ab3a installed on the tv (`install -r`, not debuggable, NLZIET was in front so
nobody got interrupted); then `adb disconnect`.

**Mid-flight:** nothing, clean break. The working tree is clean.

## Next up
1. Jordy looks at Garage on the tv (his own profile is still Standaard) and at the new reminder cards with the real remote.
2. Push main once Jordy says so (`git push` via the `github-personal` alias).
3. Garage, round 2, only if Jordy wants it: a subtle concrete texture on the background or another focus treatment.
   Keep readability from the couch the bar. The remaining hard-coded colours (Avatar, PinDialog, CalendarDialog,
   Testbeeld, SettingsScreen) don't follow the look yet.
4. Still open from earlier: Jordy adds F1 / FC Utrecht calendars on the tv and says which VIAPLAY FORMULE 1
   "versie" is live; checks Viaplay TV / FilmBox / SBS6 late-night guide; real-remote test of own guides.
5. Sync speed: `NameParser` (25 of 67 s on the tv), plan in `docs/plans/2026-10-30-sync-names-speed.md`.
6. Ideas: `docs/brainstorm.md`; Home Assistant bioscoopmodus is Jordy's pick to take up.

## Open questions
- Does Jordy's panel allow seeking inside a timeshift stream?
- BUIS icon on the Google TV home: need Jordy's description (blank tile, old icon, missing) before changing anything.

## Don't forget
- Every `adb install` stops BUIS if it's in front: check `topResumedActivity` first; always `install -r`
  (an uninstall wipes the household). The tv gets the **release** APK only (`./gradlew :app:assembleRelease`,
  then `adb -s 192.168.68.91:5555 install -r app/build/outputs/apk/release/app-release.apk`), then
  `adb disconnect 192.168.68.91:5555` so `installDebug` can't reach the tv.
- adb: `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`. In zsh, wrap it in a function; a
  `$E="-s emulator-5554"` variable doesn't word-split.
- The context-mode hook blocks gradle in Bash: run builds through `ctx_execute`.
- Parallel sessions may hold uncommitted work in this checkout: commit only your own files.
- Room schema JSON is written during the build; gh CLI is on the work account (push via SSH alias).

## Emulator gotchas
- Start the app with `am start -n nl.jordy.iptv/.MainActivity` (monkey with LAUNCHER lands on the Google TV
  account screen: Back out, never touch accounts). Picker: Right + OK = Bas (no PIN); Jordy may ask a PIN.
- Rail from Home: Left, Down×6, Right = Instellingen content; Uiterlijk is the 3rd row of Profiel.
- Clock drift after suspend: `adb -s emulator-5554 shell cmd alarm set-time $(($(date +%s)*1000))`.
- Mock server: `./gradlew :mockserver:run` (demo/demo at 10.0.2.2:8080).

## Who to ask
- Jordy: owner, tests on the real Google TV.

<!-- RESUME PROMPT: Read AGENTS.md, .agents/handoff/feature-workflows.md and the last 5 entries of
     .agents/decisions/product.md. Give a 3-bullet status, then wait for instruction. -->
