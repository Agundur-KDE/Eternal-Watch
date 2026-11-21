# Eternal Watch

Parent instructions in `/home/alec/projects/AGENTS.md` apply.

- Android app for Eternal Lands; working directory is this repository.
- Keep the GitHub repository private. Publishing or changing visibility requires
  an explicit user instruction.
- Keep internal audit reports local and ignored by Git (docs/security-audit-*.md
  and docs/audits/). Do not include them in public source packages. Keep factual
  build/test documentation separate from internal vulnerability reviews.
- EL-Klock is the functional reference, not an Android implementation. Preserve
  applicable licenses and attribution when reusing source or assets.
- Before implementing game-time and alert behavior, read EL-Klock's production
  logic, tests and documented feed failure handling. Do not guess timing rules.
- Treat stale/invalid data explicitly. A recent invasion report does not prove
  an invasion is still active; show its age rather than inventing a countdown.
- Keep notification choices per Special Day; preserve original English game names.
- Keep credentials, signing keys, private device data and build artifacts out of Git.
- Store visible app text in Android resources; avoid hardcoded UI strings.
- Fetch game data only on unmetered networks by default. Metered access requires
  the app's explicit opt-in; Data Saver still pauses metered requests. Apply the
  same central gate to foreground, service, widget and manual refresh. Bind feed
  requests to the checked Network so a Wi-Fi loss cannot silently use cellular.
  Keep cached samples visible and show paused status rather than claiming live sync.
- Required app languages: German (de), English (en, complete fallback), Spanish
  (es), Russian (ru). Keep all translations complete, including notifications,
  widgets and quantity forms. The original English game names/source text remain
  unchanged. Use language-neutral settings and stable resource IDs in tools.
