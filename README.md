# Eternal Watch

Native Android companion for **Eternal Lands**, adapting the tested clock and
alarm rules from [EL-Klock](https://github.com/Agundur-KDE/EL-Klock).

## First Android version

- Six-hour game clock and date, using 61 real seconds per game minute.
- Current Special Day, description and role-play text from public Mercator data.
- Searchable selection of 49 Special Days; only selected changes notify.
- Latest invasion report with its age, opt-in background monitoring and optional
  Android notification sound. The original unverified horn asset is not included.
- Home-screen widget with a timestamped clock/day sample and manual refresh.
- Direct browser links to our [Eternal Lands website](https://eternal-lands.agundur.de/):
  quests, dailies, Haidir, Daritha and Xaquelina. No embedded WebView or copied guide database.
- German, English, Spanish and Russian interface; game names and source text remain in English.
- No account, personal token, tracking SDK, advertising or cloud backend.

## Build

Requires Android SDK 36 and JDK 17+ (the local build uses the existing KCast-App
SDK and JDK 21). Android 8+; compile/target SDK 36, Java 17, native platform UI.
No third-party runtime libraries.

```sh
bash tools/build_apk.sh
```

Output: `dist/Eternal-Watch-debug.apk`. Build artifacts, signing keys and local
device test results are ignored by Git. Public source and signed alpha releases
are available from this GitHub repository.
Version: 0.1.0-alpha.4; an early test version, not a Google Play release.
Download the signed APK from [Releases](https://github.com/Agundur-KDE/Eternal-Watch/releases).
The local build command above produces a debug APK.

## Monitoring and widget behavior

Opening the app refreshes data and advances its clock every second. Background
monitoring is OFF by default. Enable it in Alerts & settings and grant notification
permission to continue checking while the app is closed. It uses a persistent
notification and the chosen 15–300 second poll interval (default 30 seconds).
Stop from the app or notification; that choice survives updates and restarts.
The monitor also checks that its notification channel is enabled. If Android
blocks it, background polling stops, the saved monitoring choice stays intact,
and settings show a direct link to that channel. Reopening the app after enabling
the channel restores an opted-in monitor.
If enabled, the boot receiver restores monitoring after the first device unlock.
An actual reboot and restoration after unlock were verified on the Moto g54 5G
with Android 15; other Android versions/manufacturers remain unverified.

Battery saving/Doze and manufacturer restrictions can delay polls. No wake locks,
exact alarms, full-screen alerts or Do Not Disturb bypasses are used. This is
polling, not push; immediate invasion delivery is not guaranteed. The foreground
service currently declares specialUse; Play suitability/review remains open.

The widget explicitly displays a **sample with a date/time**, not a falsely
advancing real-time clock. Refresh from its button; it also updates when the app
or monitor fetches data. Without monitoring, Android requests occasional widget
updates (nominal 30 minutes, subject to system scheduling). Actual launcher pinning
and resizing remain a separate device check.

## Data connections

Game data uses **unmetered connections only by default**, including after an
upgrade. Enable “Allow metered connections” in Alerts & settings to permit mobile
data or metered Wi-Fi/hotspots. Android determines whether a connection is metered;
the app cannot determine your actual tariff or cost.

The same rule applies to the open app, background monitor, automatic widget
updates and manual refresh buttons. Data Saver pauses metered requests even if
Android exempts this app. Cached data remains visible with its source timestamp;
the overview, monitoring notification and widget show why updates are paused.
An enabled monitor resumes automatically on a permitted connection. With monitoring
off, reconnecting does not introduce continuous background polling for widgets.
Guide links open separately in the browser and follow its network settings.

Requests are bound to the checked Android Network, so losing Wi-Fi cannot silently
move an existing request to cellular. Policy is checked again before connecting,
while reading and before accepting a response. A request already in flight when
settings change may have transferred bytes before it can stop.

The widget minimum height is now 240 dp so the pause reason and refresh button
fit alongside the cached sample in all four languages at the tested font size.
Actual launcher placement/resizing and large accessibility font sizes remain
separate checks.

Explicit USB device test (temporarily changes the connected Wi-Fi metering,
Data Saver and app monitoring choices, then verifies their restoration):

```sh
python3 tools/check_network.py --serial <USB-SERIAL>
```

This uses Wi-Fi marked as metered; it does not require a paid mobile connection.
Run with the matching debug APK installed, the phone authorized/unlocked,
notification permission granted and monitoring stopped. Ignored private recovery
state is kept under test-results if restoration cannot be verified. Run device
checks sequentially.

## Data correctness

Source: `https://mercator.holy-eternalland.de/botmanagement/elinfo.json`.
A response must have valid calendar/time bounds and positive source timestamps.
Both clock and JSON timestamps must be within three minutes of the device clock
(and no more than one minute ahead) before time/alarms are treated as fresh.
At an EL day boundary the old day is not silently extrapolated into the next day.
Outdated, invalid and disconnected states are explicit; the last valid payload
remains available as historical data. Older response timestamps cannot replace
newer samples.

Future source timestamps more than one minute ahead are rejected before cache
ordering or persistence. Previously stored implausible samples are discarded on
load; valid historical data remains available offline. Both network and cache
input pass iterative strict JSON grammar validation with a 256-KiB UTF-8 limit
and eight-container-depth limit before the platform JSON parser runs. Only expected scalar feed fields are extracted;
unknown fields are not recursively converted. Nonstandard comment/single-quote
JSON syntax is rejected. The Gradle wrapper uses checksum-pinned 8.14.8, including
its updated scripts and wrapper JAR.

The live feed sometimes puts “Today is a special day:” in GameDayName and the
actual day in GameDayDescription. Known names are resolved from the bundled
English catalog before selection matching. Matching handles case/whitespace and
curly apostrophes. New monitoring sessions start with a silent baseline; duplicate,
stale, future-dated and old invasion reports do not produce fresh alarms.
GIWS_date_diff is an absolute millisecond timestamp despite its name. Recent
reports are not evidence that an invasion is still running; no guessed countdown.

The JSON cache and preferences stay in app-private storage; no device backup.
Opening the optional source link launches the browser. Uninstalling removes the
local app data. No game credentials or personal tokens are collected.

## Verification

The production Java core is imported by 39 JVM tests. The original 14 were observed
failing against unimplemented stubs and then passing after implementation. For the
security changes, 11 regression tests were observed failing before the fixes and
then passing; a further grammar regression was observed red during review and
green after correction. Two additional new cases preserve accepted input behavior. Coverage includes
61-second timing, rollover, invalid/stale data, generic feed headings, selected-day
notifications, silent startup, duplicates, invasion toggles/age, future-cache
recovery, input size, nesting and structural preflight checks.

```sh
python3 tools/check_device.py --serial <USB-SERIAL>
adb -s <USB-SERIAL> shell am instrument -w de.agundur.eternalwatch/.DeviceChecks
```

The first tool checks real network data and visible UI, keeping screenshots only
in ignored test-results. DeviceChecks exists only in debug builds and renders the
actual production widget RemoteViews off-screen, without changing launcher layout.
It also checks cached data, all four localized notification/widget variants,
German/Russian quantity resources and the production parser against malicious
inputs entirely in memory (without modifying saved settings/cache). Results actually
observed on the test device are recorded in docs/verification.md.

## License and provenance

GPL-3.0-only; see LICENSE and NOTICE.md. Release downloads include the matching
source archive and a SHA-256 manifest. This is an unofficial Eternal Lands companion.
No EL-Klock KDE template source or unverified third-party audio was copied.


## Languages

German (de), English (en, full fallback), Spanish (es) and Russian (ru) are
required project languages. All 80 translatable resources are complete, including
widget text, channels, notifications, status/errors and website links. Russian
quantity forms include one/few/many/other; Spanish includes one/many/other.
`tools/check_translations.py` runs before every APK build to reject missing keys,
wrong format placeholders, incomplete quantity forms or missing locale registration.

Android 13+ lists these languages as app languages; settings include a shortcut
where the system supports the app-language settings screen. On older versions,
resources follow the device language with English fallback. Widget/channel text
is refreshed when configuration or app-language changes, and the running service
refreshes its notification. Date/time formatting uses the app language explicitly.
Game names and live Mercator descriptions/reports remain original English text;
the linked Agundur website opens in the browser and has its own language/content.


The interactive settings smoke test (`python3 tools/check_settings.py --serial
<USB-SERIAL>`) temporarily exercises the monitor, metered-connection and day-selection controls. It
accepts the app notification prompt and restores the saved day/monitor/metered choices
even on failure, using the matching local debug APK to restore the service.
It does not replace the newer feed cache with an older snapshot. The phone must
be authorized and unlocked. Restoration helper regressions can be run locally:
`python3 -m unittest discover -s tools -p test_settings_restore.py`.

An explicit debug-only notification posting check is available with monitoring
stopped and notification permission granted:
`adb -s <USB-SERIAL> shell am instrument -w de.agundur.eternalwatch/.NotificationChecks`.
It posts clearly labeled synthetic messages through the production Special Day
and quiet invasion notification methods, verifies Android's active notifications,
and removes those test messages before finishing. It refuses to overwrite existing
event notifications or run while monitoring is enabled. This checks posting,
content and channels, not a real feed transition, audible delivery or heads-up UI.


## Release packaging

See [the local release workflow](docs/release.md) for unsigned review artifacts,
external production signing and a matching source tarball from a clean commit.
The tools do not publish anything or change repository visibility.

Feed requests have an independent eight-second completion deadline covering
connection setup, response headers and the body. Expired requests cannot publish
cache data or alarms. A pending refresh/reconnect is coalesced and rechecks network
policy before running; abandoned loading states are cleared. I/O and abort workers
are bounded, including if platform DNS or disconnect does not respond to interrupt.
Such a platform stall can temporarily exhaust I/O slots, but callers still finish
with an error rather than accumulating workers or waiting indefinitely.
