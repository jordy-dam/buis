# Handoff — QA & testing

## What's been tested
**Unit tests (28, all green):** messy Xtream JSON parsing (fixtures shared with the mock server),
server-URL normalisation, a 12k-item catalog sync into Room, re-sync without duplicates, empty
provider answers not wiping the catalog, resume-policy boundaries (30s / 95% / unknown duration),
live .m3u8 → .ts fallback, fail-fast on 4xx.

**Emulator (Google TV image, API 36, 1080p), d-pad only via `adb input keyevent`, against the mock server:**
- [x] Login: OK opens the keyboard, wrong password shows an error, right one syncs
- [x] Channel in 3 clicks: rail → Right → Right → OK
- [x] Zap ↑/↓: first start 528 ms, zaps 65–170 ms; TS-only channel 591 ms (local network!)
- [x] Film resumed after `am force-stop` + relaunch (saved 0:39, resumed 0:39)
- [x] Back chain and focus restore; exit dialog defaults to "Blijven"
- [x] Right at the end of a row stays put; Down on detail buttons doesn't jump sideways
- [x] Instellingen: refresh runs a sync and returns to Live TV; logout (default focus "Annuleren") wipes the database and credentials, Back from login exits the app

## What still needs testing (on the real tv)
- [ ] Log in with real Xtream credentials
- [ ] Sync time and memory with the real catalog size
- [ ] Find a channel within 3 clicks
- [ ] Zap between live channels in under ~2s on the home connection
- [ ] Play a film and resume it after closing the app (Home button, and after a reboot)
- [ ] Audio on channels/films with AC3 or EAC3
- [ ] Every screen with the real remote: long-press OK for favourites, Menu key if the remote has one

## How to run the test suite
```
./gradlew test
./gradlew lint
```

## Known flaky/failing tests
- None. Robolectric needs the `--add-exports` JVM args set in `app/build.gradle.kts` on JDK 17+.
