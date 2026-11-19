# Eternal Watch

Parent instructions in `/home/alec/projects/AGENTS.md` apply.

- Android app for Eternal Lands; working directory is this repository.
- Keep the GitHub repository private. Publishing or changing visibility requires
  an explicit user instruction.
- EL-Klock is the functional reference, not an Android implementation. Preserve
  applicable licenses and attribution when reusing source or assets.
- Before implementing game-time and alert behavior, read EL-Klock's production
  logic, tests and documented feed failure handling. Do not guess timing rules.
- Treat stale/invalid data explicitly. A recent invasion report does not prove
  an invasion is still active; show its age rather than inventing a countdown.
- Keep notification choices per Special Day; preserve original English game names.
- Keep credentials, signing keys, private device data and build artifacts out of Git.
- Store visible app text in Android resources; avoid hardcoded UI strings.
