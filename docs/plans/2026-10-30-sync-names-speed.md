# Plan: faster name cleaning during catalog sync (2026-10-30)

## Why
Measured on the TCL tv (release, 2026-10-29, timing logs in a scratch build): a daily catalog sync takes
67 s after the Room REPLACE fix (was 109 s). The largest remaining cost is `NameParser.parse`: **25 s** for
~106k names (Live 4.3 / Films 15.9 / Series 4.8), about 240 µs per name. The same code on the Mac JVM costs
~4 µs per name: Android's ICU-backed regex is the slow part, so the fix is to run fewer regexes per name.
Other costs for reference: field parsing ~10 s, Room tx ~12 s (FTS 7.5 s), JSON 5.8 s, network ~5 s.

## Steps
1. **Compile once.** `classify` builds two `Regex` objects per call (the NL-SUB/AUDIO pattern and the
   "CA EN" pattern). Hoist them to `private val`s next to the others. On the JVM this saved ~20%.
2. **Cheap guards before each regex**, only where a plain character check proves the regex cannot match:
   - `catchupMark`: only when the name contains `⏺`.
   - `domain`: only when it contains `.TV` (ignore case).
   - `trailingTag` loop: only when the trimmed name ends in a bar, `]` or `)`.
   - `decoration`: only when the first or last char is in its set (incl. whitespace).
   - `spaces`: only when the string contains two spaces in a row.
   Leave `leadingTag`, `leadingCode` and `qualitySuffix` as they are in this round.
3. **Prove the output didn't change on the real catalog.** Real names may not be committed, so check on the
   tv: the temporary timing build logs one hash over all `ParsedName`s per type. Run the old and new parser
   (two installs, or both in one build) and compare hashes. Plus `NameParserTest` green.
4. **Measure on the tv** with the timing build (scratch copy, not committed), same as yesterday.

## Done when
- `NameParserTest` green, ParsedName hashes identical for Live/Films/Series on the real catalog.
- `NameParser` time on the tv from 25 s to **under 8 s**; total daily sync reported.
- If step 2 doesn't reach the target: replace `leadingTag`/`trailingTag` with small hand-written scanners
  (next round, ask Jordy first).

## Not in this round
Background sync and "profile countries first" (Jordy's two ideas): weighed in the sync-timing notes, not
started. Install on the tv only after Jordy says yes; commit nothing without asking.
