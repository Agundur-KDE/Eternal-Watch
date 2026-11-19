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
device test results are ignored by Git. The GitHub repository remains private.
Version: 0.1.0-alpha.1; this is a development APK, not a store release.

## Monitoring and widget behavior

Opening the app refreshes data and advances its clock every second. Background
monitoring is OFF by default. Enable it in Alerts & settings and grant notification
permission to continue checking while the app is closed. It uses a persistent
notification and the chosen 15–300 second poll interval (default 30 seconds).
Stop from the app or notification; that choice survives updates and restarts.
If enabled, the boot receiver restores monitoring after the first device unlock.
Actual reboot behavior for this new app still needs a device test.

Battery saving/Doze and manufacturer restrictions can delay polls. No wake locks,
exact alarms, full-screen alerts or Do Not Disturb bypasses are used. This is
polling, not push; immediate invasion delivery is not guaranteed. The foreground
service currently declares specialUse; Play suitability/review remains open.

The widget explicitly displays a **sample with a date/time**, not a falsely
advancing real-time clock. Refresh from its button; it also updates when the app
or monitor fetches data. Without monitoring, Android requests occasional widget
updates (nominal 30 minutes, subject to system scheduling). Actual launcher pinning
and resizing remain a separate device check.

## Data correctness

Source: `https://mercator.holy-eternalland.de/botmanagement/elinfo.json`.
A response must have valid calendar/time bounds and positive source timestamps.
Both clock and JSON timestamps must be within three minutes of the device clock
(and no more than one minute ahead) before time/alarms are treated as fresh.
At an EL day boundary the old day is not silently extrapolated into the next day.
Outdated, invalid and disconnected states are explicit; the last valid payload
remains available as historical data. Older response timestamps cannot replace
newer samples.

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

The production Java rules are imported by 14 JVM tests. They were observed failing
against unimplemented stubs and then passing after implementation. Coverage includes
61-second timing, rollover, invalid/stale data, generic feed headings, selected-day
notifications, silent startup, duplicates and invasion toggles/age.

```sh
python3 tools/check_device.py --serial <USB-SERIAL>
adb -s <USB-SERIAL> shell am instrument -w de.agundur.eternalwatch/.DeviceChecks
```

The first tool checks real network data and visible UI, keeping screenshots only
in ignored test-results. DeviceChecks exists only in debug builds and renders the
actual production widget RemoteViews off-screen, without changing launcher layout.
It also checks cached data, all four localized notification/widget variants,
and German/Russian quantity resources. Results actually
observed on the test device are recorded in docs/verification.md.

## License and provenance

GPL-3.0-only; see LICENSE and NOTICE.md. The repository is private; no source or
APK publication has been made. This is an unofficial Eternal Lands companion.
No EL-Klock KDE template source or unverified third-party audio was copied.


## Languages

German (de), English (en, full fallback), Spanish (es) and Russian (ru) are
required project languages. All 71 translatable resources are complete, including
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
