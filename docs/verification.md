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
