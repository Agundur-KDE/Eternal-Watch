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

Completed the following feed-input and build-toolchain corrections:

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

## Continued device verification – 21 November 2026

Moto g54 5G connected and authorized; device reports Android 15. No Android 16
result is claimed. The overview shows a synchronized Mercator sample, Normal Day
and readable German UI; its local ignored screenshot was inspected. An initial
overview run overlapped instrumentation and could not see the activity; a separate
repeat passed. Future UI and instrumentation checks should run sequentially.

The complete settings smoke test passes again: foreground monitor start/stop,
select all 49 days, clear, search and select Reconstruction Day. Original day
selection and monitoring choice were restored, preserving the current cache.
All three local restoration helper tests pass.

Added an explicit debug-only NotificationChecks instrumentation with localized
synthetic test bodies in DE/EN/ES/RU. On the actual Moto it called the production
Special Day and quiet invasion notification methods. Both were present in Android
active notifications with the expected channel, title, test body and content intent;
the quiet channel has no sound. Test notifications are canceled before finishing.
The check refuses existing event notifications and enabled monitoring to avoid
overwriting real alerts. It neither injects feed data nor modifies user choices.
This does not verify a real feed event transition, audible delivery, heads-up
presentation or a user tapping the notification.

The new debug APK was built and installed. All 28 JVM tests, translation checks
and lintDebug pass; existing toolchain/deprecation warnings remain. The general
DeviceChecks instrumentation passes again on that APK (Android parser security,
cached sample, four languages, notification construction, widget rendering/actions
and quantity forms).

Corrected check_device.py's output: finding saved JSON proves cache presence,
not a new network request. The UI synchronization status is reported separately.

Actual launcher pinning/resizing and long-term battery/Doze behavior remain open.

### Actual reboot restoration – 21 November 2026

User explicitly authorized a real phone reboot. Original monitoring was off,
with no monitor_enabled key stored and no foreground service. Saved that exact
preference state and the pre-reboot kernel boot ID locally in ignored test-results.
Enabled monitoring through the real settings UI and verified the persisted choice
and running foreground service before sending adb reboot.

After the device returned and the user unlocked it, read-only ADB checks confirmed
a changed kernel boot ID, sys.boot_completed=1, user state RUNNING_UNLOCKED and
WatchService running with isForeground=true; monitor_enabled remained true. No
manual activity launch, service start, synthetic boot broadcast or APK replacement
was issued between the reboot and this observation. Reboot restoration passes on
this Moto with Android 15. The observation establishes the state after unlock,
not the exact startup latency or behavior before first unlock.

Then restored the original absent monitor_enabled key and inactive service,
retaining all other current preferences and latest feed data. The same debug APK
was reinstalled only after the successful reboot observation, to restore normal
package state using the established settings-test recovery path. Verified exact
monitor preference restoration and no running foreground monitor.

The local ADB helper initially used cmd user is-user-unlocked, which this Android
version does not support. Replaced that read-only query with the supported
am get-started-user-state 0 and required RUNNING_UNLOCKED before evaluating the
result. No application behavior was changed for this test.

## Metered connection policy – 21 November 2026, alpha.3

Implemented the user's requested default of unmetered-only game data, with an
explicit allow_metered choice in settings. New and existing installations default
to false when the key is absent. Android network capabilities determine metering,
not whether the transport is Wi-Fi or mobile. ACCESS_NETWORK_STATE is the only
new permission (a normal permission without a runtime prompt).

The central request gate applies to the open activity, foreground service, widget
updates and manual refresh, including forced requests. Data Saver pauses metered
requests even if the app is exempt from Android's background restriction. No
validated internet connection, metering restrictions and Data Saver each have an
explicit localized pause reason in the overview, ongoing notification and widget.
Cached source data/timestamps remain available. Feed requests are bound to the
checked Network and recheck policy before connection, during reads and before
accepting a response. Existing in-flight bytes cannot be undone; this is not a
zero-byte guarantee at the instant of a policy change.

Default-network/capability callbacks, Data Saver broadcasts and the preference
listener update status and resume fetching for an open app or enabled monitor.
Widget-only use does not acquire continuous reconnect polling. No game-time,
alarm-selection or feed-validity rules were changed. External browser guide links
follow browser settings, as explained in the localized help.

Observed final verification on the Moto g54 5G, Android 15:

- VersionCode 3 / 0.1.0-alpha.3 debug APK built and installed. All 33 JVM tests
  (28 existing plus five network policy tests), translation validation for 78
  resources in DE/EN/ES/RU, lintDebug and three settings-restoration tests pass.
- tools/check_network.py passes against real Android Wi-Fi metering overrides:
  unmetered ALLOWED, metered METERED by default, metered ALLOWED after explicit
  opt-in, and DATA_SAVER despite opt-in. Blocked ordinary and forced requests
  complete without modifying cache/error; permitted requests receive valid data.
- Four-language production RemoteViews and notification construction show the
  expected pause reason. The previous declared widget minimum clipped the refresh
  control; its minimum height is now 240 dp. Layout checks at the actual declared
  minimum width/height pass in all four languages for allowed and paused states.
- The real foreground monitor shows a paused notification on metered Wi-Fi. After
  closing its activity and making that Wi-Fi unmetered, it automatically performs
  a successful feed request and returns the notification to the monitoring state.
- The network test verifies restoration of exact original Wi-Fi override values,
  Data Saver and all app choices (excluding the intentionally refreshed cache and
  its status), including absent default keys. The temporary service is stopped.
- The extended real settings UI test passes: monitor start/stop, metered switch
  persistence, all/clear/search/individual-day selection. Original day, monitor
  and metered choices are restored while preserving the latest cache. General
  DeviceChecks instrumentation also passes on the final APK.

The first metering run observed a temporary unvalidated network; device checks
now allow Android's capability updates to settle before asserting the state.
AOSP's setMeteredWifiNetwork shell implementation returns -1 even on success;
the tool narrowly handles that case and verifies settings by reading them back.
An initially over-strict restoration comparison detected XML indentation rather
than a changed sound setting; comparison now ignores XML tail whitespace while
preserving actual values. Exact restoration was rechecked successfully.

These tests use the existing Wi-Fi connection marked as metered; they do not
claim paid cellular traffic or a physical Wi-Fi-to-LTE handover was exercised.
Actual launcher placement/resizing, large accessibility fonts, Android 16,
real-event/audible alert delivery and long-term battery/Doze remain separate
checks. Prior alpha.2 reboot verification is not a new alpha.3 reboot test.

## Local release build checkpoint – 21 November 2026

assembleRelease and lintRelease pass with zero lint errors and seven warnings.
The unsigned release APK has no debug flag, test instrumentation or QA classes.
The distribution APK remains debug-signed. No artifact has been published.
Production signing, a matching full GPL source package and a smoke test of the
exact signed release artifact remain outstanding. Play submission requirements
are separate. The 33 JVM tests were independently recompiled and passed again.

## Request lifecycle and monitor checks – 21 November 2026, alpha.4

Updated the complete wrapper to checksum-pinned Gradle 8.14.8. The distribution
hash was fetched from Gradle's official endpoint; the regenerated wrapper JAR
matches its official SHA-256 reference. The JAR hash is unchanged from 8.14.7.

Requests now have an independent eight-second logical completion budget covering
connection setup, headers and body. Late I/O cannot submit accepted results.
Prepared immutable results are processed in completion order outside the request
lock; transient status belongs to the latest attempt. One pending refresh is
coalesced and rechecks current network policy/monitor interest. I/O and abort
workers are capped; a platform call that ignores interruption can temporarily
occupy an I/O slot, but does not hold logical completion or grow worker queues.

The monitor checks the actual notification channel as well as app permission.
A blocked channel stops polling while retaining the user's saved choice. Settings
show the channel and open its Android settings directly. Reopening after enabling
the channel resumes an opted-in monitor. Silent LOW importance remains supported.

Observed checks on versionCode 4 / 0.1.0-alpha.4:

- Debug APK build, 39 JVM tests, lintDebug and complete 80-resource translation
  sets pass. The six request-coordinator regressions cover actual slow HTTP
  headers, late results, coalesced overlapping requests, bounded worker exhaustion,
  elapsed budget checks and delayed UI processing with a later failed request.
- Alpha.4 installed on the Moto g54 5G. DeviceChecks passes the Android parser,
  locale/widget checks and actual disposable NONE/LOW notification channels.
- Real Android channel UI test passes: blocking the actual monitor channel stops
  the foreground service; saved monitoring remains enabled; the app shows the
  channel warning and does not restart while blocked. Enabling the channel and
  reopening the app resumes the service. Original app values and channel state
  are restored, preserving the latest feed cache.
- Full network device checks pass again for unmetered/metered/explicit opt-in/
  Data Saver and automatic background resume, with exact original settings and
  stopped temporary service verified afterwards. Three settings-restoration
  helper tests pass.

The local packaging workflow is documented in release.md. It requires a clean
commit, builds from an isolated extraction and stages complete artifact sets
before exposing them. Production signing identity, exact signed-release device
test and any account-side Play requirements are separate from these debug checks.
No public distribution has been performed.

## Release packaging regression check – 21 November 2026

The full packaging regression tool now passes with a disposable test signing key:
valid APK and AAB signatures, matching certificate and artifact hashes, exclusion
of an ignored asset from both binary and source, unchanged existing artifact sets,
and no final artifacts after a wrong-certificate failure. Test keys and signed
test artifacts were removed. This is not a production signing/device test.

The first real signing run exposed apksigner's sequential password-file reads
when store and key password paths are the same. The packager now creates separate
owner-only temporary copies outside build inputs and source packages. The retained
regression uses the same password path for both fields and passes with the actual
Android/JDK signing tools. Internal logs/results remain ignored by Git.

## Published production APK device test – 21 November 2026

The exact APK published in v0.1.0-alpha.4 was installed on the Moto g54 5G with
Android 15. Its installed APK SHA-256 matches the release asset:
`3d4e0483614c934c3c08d0cdad73d61726bbf44827a114e6d1a61e1936053bb2`.
The package reports 0.1.0-alpha.4 and is not debuggable. The preceding debug APK
and preferences were backed up locally before the incompatible signing-key
replacement. No production instrumentation or run-as access was enabled.

Observed through the actual release interface:

- Live Mercator synchronization, game date and Special Day display; the visible
  seconds advance when enabled and the original display choice is restored.
- Notification permission flow; foreground monitoring starts, continues with
  the activity closed, and stops through the app switch.
- Metered connection choice survives a normal close/process restart and is
  restored to its original value.
- Select all 49 days, clear, search and individual selection work. The individual
  selection and its later clearing persist across process restarts.
- The About dialog displays the installed release version. Original effective
  app choices and notification permission are restored, with no monitor left
  running. The production release remains installed.

Checks were completed in short UI sections after automatic screen locking
interrupted the initial run. The temporary screen-timeout extension was restored
and read back. Screenshots, original debug backup and per-device result records
are local ignored artifacts. This closes the pending signed-release smoke test;
launcher placement/resizing, Android 16, real-event notification sound and
long-term battery/Doze checks remain separate.
