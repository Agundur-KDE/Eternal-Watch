# Local release preparation

Nothing in these tools uploads an app, publishes a GitHub release or changes
repository visibility. The GitHub repository stays private. Internal audits,
device results, credentials and signing keys are excluded from source packages.

## Requirements

Use JDK 17+, Python 3.9+, Android SDK 36 and Android build tools. Set JAVA_HOME
and ANDROID_HOME; optionally set GRADLE_USER_HOME. The shell helpers retain the
existing sibling KCast-App toolchain fallback on the development workstation.
The checksum-pinned Gradle wrapper downloads Gradle 8.14.8.

Commit every intended source change first. The packaging tool requires a clean
working tree and verifies it again after building. The source tarball contains
the complete tracked Android sources, tests, Gradle wrapper/build files, tools,
license/provenance files and public build documentation from exactly that commit.
No Git history, .local, signing config, internal audits or build outputs are
included. Runtime platform/SDK libraries are not bundled into the source archive.
Compilation runs in a private temporary extraction of that archive. Ignored
files in the development checkout cannot become accidental APK assets. Verified
artifacts appear together through an atomic directory rename. Existing version
directories are never overwritten, including after a failed signing attempt.

## Unsigned review artifacts

```sh
bash tools/build_release.sh --unsigned
```

Add --bundle to also prepare an Android App Bundle. Files are placed in ignored
dist/ and named with the app version and source commit. UNSIGNED is part of every
unsigned binary filename. An unsigned APK cannot be installed as a normal app.
The JSON manifest records the commit, version, signing status and SHA-256 hashes
for the binary and matching source tarball.
Each complete set lives in its own version/commit directory under dist/. The
optional --output-dir argument selects a different local destination for checks.

## Production signing

A signing identity must be chosen separately and retained for future updates.
Do not use the Android debug key. Store the keystore, password files and signing
configuration outside Git with owner-only permissions. Keep an independent,
secure backup of the chosen key and its credentials. The packaging tool never
creates a key, prints passwords or changes signing identities automatically.

An ignored .local/signing.json may have these fields (paths are examples):

```json
{
  "keystore": "/private/path/eternal-watch.p12",
  "alias": "eternal-watch",
  "store_password_file": "/private/path/store-password.txt",
  "key_password_file": "/private/path/key-password.txt",
  "certificate_sha256": "EXPECTED_64_HEX_CERTIFICATE_SHA256"
}
```

```sh
bash tools/build_release.sh --signing-config .local/signing.json
```

The tool verifies the signed APK against the expected certificate, rejects the
Android debug certificate, and checks the release manifest/DEX for debug flags,
instrumentation and debug-only classes. Passwords are supplied to signer tools
through files. A signed AAB is strictly verified against the expected keystore and
alias; its embedded certificate must match the APK certificate fingerprint.
Store and key password paths may be identical: the packager makes separate private
temporary copies because apksigner consumes lines when a password path is reused.
Add --bundle only if a signed AAB is required. Play App Signing,
upload-key registration and any cross-store signing strategy still require the
corresponding account configuration; generating an AAB does not complete them.

## Test and distribute

Test exactly the signed APK and certificate intended for distribution. A
production-key APK cannot update a debug-key installation with the same package
ID. Preserve the existing test installation/data; use a separate device or
explicitly arrange its replacement before installing an incompatible signature.

Offer the matching complete source tarball, LICENSE and NOTICE with the APK
download so recipients can obtain the source corresponding to that binary.
Repository visibility need not change for this source-download route. Validate
the source archive and release hashes before any publication.

From an extracted source archive, builds do not require access to the private Git
repository: set the SDK/JDK environment and run bash tools/build_apk.sh or
./gradlew --no-daemon assembleRelease. Recipients can sign their own unsigned
builds. The packaging/provenance tool itself requires a Git checkout.

Public download hosting remains a separate authorized action. A Play submission
also needs an accessible app privacy policy/contact, accurate Data Safety answers
and the specialUse foreground-service justification/review. These account-side
requirements are not completed by local packaging. In particular, do not mark
the app as collecting no data without assessing the actual Mercator requests and
its operator's data handling.

## Packaging regression check

```sh
python3 tools/check_release_packaging.py
```

This builds with a temporary test key and a deliberately ignored asset. It checks
APK/AAB signatures, matching artifact hashes, exclusion of that asset and internal
audits, refusal to overwrite existing artifacts, and rejection of a wrong expected
certificate. Test credentials and signed test artifacts are removed afterwards;
no production signing identity is created. Logs/results stay in ignored test-results.
