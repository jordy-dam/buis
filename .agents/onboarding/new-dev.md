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

Tv on Ethernet (our living-room TCL Smart TV Pro): wireless debugging can't be switched on without
Wi-Fi. Turn on *USB debugging* instead; the tv then accepts adb over the network on port 5555:
`adb connect <tv-ip>:5555`, accept "Allow debugging" on the tv (tick "always allow"), then install.
That tv is 32-bit (`armeabi-v7a`), which is why the APK keeps that ABI.

Without Android Studio: `brew install --cask android-commandlinetools`, accept the licences with
`sdkmanager --licenses`, install `platform-tools`, `platforms;android-37.0`, `emulator` and
`system-images;android-36;google-tv;arm64-v8a`, and put `sdk.dir=…` in `local.properties`.

Xtream credentials go in the app's login screen, never in the repo. For development, use the mock
Xtream server (dev only, never in the APK):

```
./mockserver/generate-media.sh     # once; needs ffmpeg, output is gitignored
./gradlew :mockserver:run          # http://127.0.0.1:8080, login demo / demo
```

In the emulator, log in with server `10.0.2.2:8080`. Channels whose id ends in 7 have no HLS, to
exercise the .ts fallback. Zap times are logged in debug builds under logcat tag `Zap`.

## 3. Where things live
- Build spec and phases: `docs/spec/build-prompt.md`
- Agent context and conventions: `AGENTS.md`
- Decisions log: `.agents/decisions/`
- Current state of the work: `.agents/handoff/`

## 4. House rules
- Branches: `feat/…`, `fix/…`, `chore/…`. `main` stays installable on the tv.
- Never commit credentials, server URLs, playlists or signing keys (see `.gitignore`).
- Only build what's in the current phase.
