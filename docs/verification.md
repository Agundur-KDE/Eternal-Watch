# First Android checkpoint – 19 November 2026

Version 0.1.0-alpha.1, package de.agundur.eternalwatch, installed on Moto g54 5G.

Confirmed:

- Debug APK builds; Android lint: 0 errors, 5 non-blocking warnings.
- 14 production-core JVM tests observed failing before implementation, then passing.
- Actual Mercator HTTPS response fetched, parsed and cached on the phone.
- Actual overview captured and inspected: German UI, running EL clock/date,
  correct canonical Special Day from the generic live feed heading, readable cards.
- User independently viewed the app and approves the appearance.
- Debug instrumentation on the phone: cached live sample, German singular/plural,
  production RemoteViews inflation, sample timestamp and bound refresh action pass.
  The launcher layout was not changed. Widget provider is registered.
- EN/DE resource sets complete (61 translatable entries); bundled GPL matches root.

Not yet confirmed:

- Settings automation was interrupted by the app not being visible/phone locking;
  do not claim complete UI selection or foreground-service toggle tests passed.
- Actual new Special Day/invasion notification delivery on the device.
- Launcher widget pinning/resizing, actual reboot restore, Android16 and long-term
  battery/Doze behavior. Background monitoring is opt-in, off by default.

Local screenshot test-results/overview.png is ignored and not committed.

User requested saving this visual/functional baseline before exploring alignment
with https://eternal-lands.agundur.de/ and possible quests/dailies content.


## Agundur website links

After baseline commit 25820b6, the user chose direct browser links only.
Added an Agundur guide card after the Special Day card with Quests, Dailies,
Haidir, Daritha, Xaquelina and the site homepage. Live feed attribution to
Mercator remains accurate. No guide content copied and no WebView added.
Build, all 14 core tests, lint0errors and complete EN/DE resource keys pass.
The earlier settings automation did not complete because the app became
not visible/phone locked; do not upgrade that result to passed.


## Required four languages – 19 November 2026

User explicitly requires DE, EN, ES and RU (website visitor countries shown as
context). This preference is now in AGENTS.md. All 71 translatable resources have
complete key/type/placeholder coverage; Android app-language registration and
quantity forms are checked automatically before builds. English stays fallback;
original English game names and live feed text are preserved.

Updated APK installed on the Moto. Build + 14 core JVM tests and lint0errors pass.
Extended debug instrumentation passes on the actual phone: English, German,
Spanish and Russian refresh labels, localized foreground notification titles,
actual RemoteViews widget labels/rendering, plus Russian one/few/many cases at
1,2,5,21. It restores channel names to the current app language; no global device
locale or launcher layout was modified. Static EN/DE/ES/RU argument and quantity
validation passed. These checks do not claim every screen/font-size combination
or an actual event transition was manually tested in each language.


## Resumed device checks after the pause

The user returned the Moto with debugging enabled. ADB currently reports
unauthorized; no device settings have been read or changed in this resumed
attempt. USB-debugging approval and an unlocked visible app are still required.

The settings test tool now accepts existing day/monitor choices, captures them
in memory and restores only those keys in a finally block. It preserves the
latest feed cache and unrelated preferences. Restoration uses a stopped app,
atomic preference-file replacement and reinstall of the matching local debug APK
so the real update receiver can restore the saved monitor choice. APK version
is checked before mutations. Three local regression checks pass for old choices,
missing defaults, latest-cache preservation and non-mutated input trees.
This restoration path has not yet been exercised against the returned phone.
Actual settings/monitor UI checks remain unconfirmed until USB approval.


## Authorized Moto: partial settings checks and keyboard correction

USB authorization resumed after restarting the local ADB server; user confirmed
the new prompt. Actual UI runs confirmed monitor start and stop with a foreground
service and persisted choice. Select-all and clear passed after the test tool
waited for asynchronous UI/SharedPreferences.apply() writes. The restoration
finally path ran on device and restored the original choices while retaining
the latest cache, even after a failed selection check.

The individual filtered-day tap remained unconfirmed with the keyboard open.
The app now includes IME bottom insets and a search-key action that dismisses
the keyboard; the tool submits that action before selecting the day. A subsequent
attempt was blocked by automatic screen lock. Confirmed via window policy:
keyguard showing, SCREEN_STATE_OFF. A debug-only qa_keep_awake launch option
now keeps only the visible test activity awake; no global screen setting changes.
Updated APK installed; build, 14 core tests, translations and lint0errors pass.
A final complete settings run still requires the user to unlock the phone.
No claim that the individual selection correction passed on device yet.

## Complete settings check – 20 November 2026

The full `tools/check_settings.py --serial <USB-SERIAL>` run passed on the
authorized Moto g54 5G with exit code 0. The real UI confirmed:

- Monitoring starts the foreground service and stops it again, including the
  persisted restart choice.
- Select-all saves all 49 Special Days; clear removes the selection.
- Searching for Reconstruction Day, submitting the search keyboard action and
  tapping the individual day saves exactly that day. This confirms the earlier
  keyboard correction on the device.
- The finally path restores the original day selection and monitoring choice,
  while preserving the latest feed cache.

The initial attempt was blocked before app mutations because the sandbox could
not reach/start the ADB server (`smartsocket` listener: Operation not permitted).
Running the same script with approved access outside the sandbox resolved that
tooling issue; future device runs require that access as well.

This completes the previously interrupted settings check. Actual notification
delivery for a new game event, launcher widget pinning/resizing, reboot restore,
Android 16 and long-term battery/Doze behavior remain unconfirmed.

## Security remediation – 20 November 2026, alpha.2

Addressed the three findings in `security-audit-2026-11-20.md`:

- Updated Gradle from 8.11.1 to 8.14.7 with the official distribution checksum,
  regenerated wrapper JAR/scripts and checked the JAR against the official hash
  `ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0`.
  AGP remains 8.10.1; the complete debug build passes with this combination.
- Source timestamps over one minute in the future cannot enter cache ordering or
  persistence. Existing implausible cache entries are discarded when loading;
  ordering also permits recovery from an already poisoned in-memory sample.
  Historical samples and the original clock/alarm freshness rules remain valid.
- Network and cached raw input are bounded to 256 KiB of UTF-8 and eight nested
  containers before the recursive Android parser is invoked. Complete JSON grammar
  validation is iterative and rejects lenient extensions. Expected fields are
  extracted as scalars; arbitrary recursive object-to-Map conversion is removed.

TDD evidence: with the new tests and no-op protection stubs, 27 JVM tests ran,
11 failed by assertion (five timestamp regressions and six input-guard tests).
After the first implementation all 27 passed. The two other new cases verify that ordinary
input, historical data and the exact one-minute skew boundary remain accepted.

The independent Astra follow-up caught a grammar mismatch in the first structural
scanner: quotes embedded in Android's unquoted literals could hide nesting from
the scan. An additional regression (`rejectsUnquotedTokensAndLenientGrammarBeforePlatformParser`)
was observed failing with 28 tests / one failure. The scanner was replaced with
complete iterative JSON grammar validation: explicit object/array states, quoted
keys, checked escapes, strict numbers/literals and separators. All 28 tests then
passed. Android checks additionally reject unquoted embedded quotes, leading-zero
numbers and trailing commas. The version in the Info dialog now comes from the
installed package instead of a duplicated alpha.1 literal.

Final `bash tools/build_apk.sh`: assembleDebug, 28 JVM tests, all four translation
sets and lintDebug pass. Lint reports zero errors and seven warnings (existing
preference writes, platform locale attribute, debug locale checks, package query,
unused widget description and clock placeholder). Three settings restoration
helper tests also pass. Gradle reports deprecated features for a future Gradle 9
migration; the SDK toolchain reports its existing XML-version warning.

Installed versionCode 2 / 0.1.0-alpha.2 on the authorized Moto g54 5G.
Actual `DeviceChecks` instrumentation passed: the production Android JSON parser
rejects 5,000 nested objects/arrays, oversized multibyte strings, future timestamps,
structured/boolean values in expected scalar fields and comment syntax. Plain and
wrapped healthy data, bounded unknown fields, null optional text, historical data
and recovery after invalid inputs pass. These inputs exist only in memory and do
not replace the user's cache. Existing live-cache, DE/EN/ES/RU notification/widget,
widget rendering/action and quantity checks pass in the same run.

This does not constitute an exhaustive second security audit. Repository content
filtering/dependency verification remain optional build hardening; a production
release-signing check still requires an actual release artifact. The earlier
outstanding real-event, launcher, reboot and long-term behavior checks remain open.

Final targeted Astra static review approved all three original remediations,
including the complete grammar validator, and the corrected version display.
The reviewer inspected source/tests without executing its own test run.
