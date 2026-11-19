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
