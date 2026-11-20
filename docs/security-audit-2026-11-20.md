# Independent security audit – 20 November 2026

Reviewer: separate GPT-6.2-Astra agent, started without conversation history.
Scope: commit `ca37498defc86e6559e446c3fdae347e968a5b23`, Android sources,
manifest, build configuration and existing debug APK. The only prior local
change was the verification log. No application code, device or server changes.

## Findings

### 1. High under the stated build-host conditions: vulnerable Gradle version

`gradle/wrapper/gradle-wrapper.properties:3` selects Gradle 8.11.1. Manufacturer
advisories identify this version as affected by daemon and dependency-resolution
vulnerabilities:

- [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64):
  a local attacker able to reach a running build daemon may execute code as the
  build user.
- [GHSA-pcp8-98xm-c6pr](https://github.com/gradle/gradle/security/advisories/GHSA-pcp8-98xm-c6pr):
  stale daemon registry risk on shared build hosts after abnormal termination.
- [GHSA-j5m7-59rp-24f5](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5):
  TLS failures at a preferred repository can cause fallback to a later repository.
  Exploitation requires disrupting that connection and controlling a matching
  artifact in a later repository.

`settings.gradle:1–4` uses multiple unfiltered repositories; no versioned Gradle
dependency-verification metadata was found. Version exposure is confirmed, not
compromise of this workstation. This is a build-system finding, not a demonstrated
remote attack against installed apps.

Recommendation: update the complete wrapper to a patched version compatible with
the Android Gradle plugin, then run the required build checks. Gradle 8.14.7
contains fixes for these three advisories. Consider repository content filtering
and dependency verification as additional protection.

### 2. Medium: future feed timestamp persistently blocks healthy updates

Evidence: `GameSnapshot.java:33–39,56`, `FeedRepository.java:34,73–77`,
`AlarmEngine.java:10` under `app/src/main/java/de/agundur/eternalwatch/` (the
snapshot and alarm classes are in `core/`).

The parser accepts positive timestamps far in the future. Cache selection compares
only the feed timestamp. The repository persists the winning sample even when
freshness checking rejects it. On restart that sample is loaded again. Later
healthy responses with lower timestamps cannot replace it, preventing recovery
of the clock and alarms until the feed catches up or the cache is repaired.

Prerequisite: erroneous or malicious content from the fixed HTTPS feed endpoint.
No arbitrary-app or ordinary network-observer path to supply it was found.
This is a static code finding; no exploit harness was executed.

Recommendation: reject implausible future timestamps before comparison/persistence,
and invalidate existing implausible cache ordering values on load. Preserve valid
historical samples for offline display.

### 3. Medium: unrestricted JSON nesting can exhaust the stack

Evidence: `FeedRepository.java:25–29,67,71,78–79`.

The 256-KiB response limit does not bound JSON nesting. The Android JSON parser
recursively parses nested structures, and the app recursively maps all object
fields, including unknown fields. A sufficiently deep response can cause
`StackOverflowError`, which the exception handlers do not catch, potentially
terminating the app and its monitor. The same problematic response can be fetched
again. Prerequisite: control of, or an error in, the configured HTTPS feed.

This is statically derived. No device-specific crash threshold or actual crash
was measured. Parser reference:
[Android JSONTokener](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-30/%2B/refs/heads/androidx-room-release/org/json/JSONTokener.java).

Recommendation: bound nesting before recursive parsing; accept only expected
fields and scalar types. Apply the same limits to network and cached data.

## Other checked areas

- Exported startup receiver validates actions, unlocked-user state, saved opt-in
  and notification permission. Accepted actions are protected system broadcasts.
  Service, widget receiver and language receiver are private. Reviewed
  PendingIntents are explicit and immutable. No external intent path to replace
  the feed endpoint or enable monitoring was found.
- Fixed HTTPS endpoint, standard platform TLS validation, no redirects, request
  timeouts and body-size limit. No game credentials or alarm selections are sent
  by the reviewed fetch code. The feed operator receives normal connection data,
  including source IP.
- Private SharedPreferences; backup and device transfer excluded. Feed strings
  are displayed as native text. Limiting stored/displayed field lengths and
  retaining only required fields would provide additional hardening.
- Monitoring defaults off, requires notification availability, uses a foreground
  notification and preserves the user's stop decision.
- Existing APK inspected with aapt2 and apksigner: debuggable, DeviceChecks
  instrumentation present, valid Android debug signature. This matches its
  documented development purpose; no release APK was available.
- APK SHA-256:
  `7382e653d57e4ee4fa396c9962fd36894af4a2c915ff519bd7c21cf9ebd6d917`.
  Distribution APK matches the existing debug output.
- No third-party runtime dependencies declared; JUnit is test-only. Gradle ZIP
  checksum matches the official reference. Wrapper JAR matches an official Gradle
  hash. Common secret-pattern search in tracked files found no matches. This
  does not establish absence of secrets throughout Git history.

## Limits and follow-up

No dynamic exploit tests, release build, device tests, feed-server penetration
tests, personal-device-data inspection or exhaustive transitive dependency/Git
history audit were performed. No critical finding or demonstrated data theft was
identified within this scope; this is not a security guarantee.

Recommended order: patch Gradle, add a failing recovery regression for future
timestamps and fix cache handling, then add bounded-input tests and parsing
limits. Inspect a separately signed release artifact before publication.
None of these remediations has been applied by this audit.

## Remediation follow-up – 20 November 2026

Subsequent implementation (alpha.2) addresses all three numbered findings:

1. Checksum-pinned Gradle 8.14.7 plus regenerated wrapper scripts/JAR. JAR hash
   independently matches the official published checksum. The existing AGP 8.10.1
   builds successfully with the updated Gradle.
2. Future-source rejection before cache selection/persistence; invalid persisted
   entries are removed on load, and previously poisoned ordering values cannot
   block a healthy replacement. Historical offline samples remain supported.
3. Iterative complete JSON grammar/byte/depth validation before JSONObject for both network and cache;
   eight-container maximum and the existing 256-KiB cap applied to UTF-8 cache
   strings as well. Extraction accepts only expected scalar fields and no longer
   recursively maps unknown structures.

Validation: 11 new regression tests observed red before implementation. The
independent Astra remediation review found a quote-boundary mismatch between the
first structural scanner and Android's lenient unquoted literals. An additional
grammar regression was observed red, and complete iterative grammar validation
replaced that scanner; all 28 JVM tests are green. Debug build, translation validation and lint pass
(zero lint errors). Actual Moto instrumentation additionally exercises the
production Android parser with hostile inputs entirely in memory, followed by
healthy inputs; all checks pass, together with existing locale/widget checks.
See `verification.md` for exact evidence and remaining verification limits.

A targeted independent Astra review of the remediations was performed, not an
exhaustive second audit or supply-chain verification.
The final static review approved closure of all three original findings and the
version-display regression. The reviewer did not execute its own test run.
Repository content filtering, dependency verification and production release
signing remain separate hardening/release work, not claimed complete here.
