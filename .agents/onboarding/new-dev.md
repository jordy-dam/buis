# Onboarding — new developer

## 1. What this repo is
A personal IPTV app for Google TV, built in Kotlin with Compose for TV and Media3. It logs in to
an Xtream Codes server and shows Live TV, Films and Series in a 10-foot UI. It's built in three
phases; see `docs/spec/build-prompt.md`.

## 2. Get it running
Needs Android Studio (latest stable, with JDK 17+) and the Android TV emulator image (1080p).

```
./gradlew assembleDebug
./gradlew test
./gradlew installDebug        # emulator or tv connected via adb
```

On the real Google TV:
1. Settings → System → About → click *Android TV OS build* 7× to unlock developer options.
2. Developer options → turn on *Wireless debugging* (on older versions: *Network debugging*).
3. `adb pair <tv-ip>:<pair-port>` with the code shown on the tv, then `adb connect <tv-ip>:<port>`.
4. `./gradlew installDebug`

Xtream credentials go in the app's login screen, never in the repo. For development, use the mock
Xtream server (added in phase 1).

## 3. Where things live
- Build spec and phases: `docs/spec/build-prompt.md`
- Agent context and conventions: `AGENTS.md`
- Decisions log: `.agents/decisions/`
- Current state of the work: `.agents/handoff/`

## 4. House rules
- Branches: `feat/…`, `fix/…`, `chore/…`. `main` stays installable on the tv.
- Never commit credentials, server URLs, playlists or signing keys (see `.gitignore`).
- Only build what's in the current phase.
