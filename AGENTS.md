# SubHub agent workflow

These repository rules apply to every automated or human-assisted change.

## Branch and commit discipline

- `master` is the release branch. Keep it buildable; use a topic branch and pull request for work that is not ready to ship.
- Keep commits narrowly reviewable and start imperative subjects with one of: `[feat]`, `[fix]`, `[perf]`, `[refactor]`, `[docs]`, `[test]`, `[build]`, `[ci]`, `[chore]`, or `[revert]`.
- Commit messages must say what changed and why. Record user-visible behavior, migration or compatibility concerns, and the checks run in the commit body when the subject alone is not enough.
- Do not commit credentials, signing keys, local properties, purchased APKs, decompiled trees, captures, or generated build outputs.

## Versioning

`version.properties` is the only release-version source:

- `VERSION_NAME` uses semantic versioning.
- `VERSION_CODE` is a positive Android integer and must increase for every released APK.
- Any release-bound change must update both values in the same commit. The helper performs the safe increment:

  `python scripts/release_version.py --set-version 0.2.0`

- Run `python scripts/release_version.py` after editing the file.
- A release tag must be exactly `v<VERSION_NAME>` and point at the tested release commit. Never move or reuse a published tag.

## Required verification

Before merging or tagging a release, run:

`./gradlew testDebugUnitTest lintDebug assembleDebug`

The universal APK is `app/build/outputs/apk/debug/app-universal-debug.apk`. It includes every ABI supported by the project; per-ABI APKs are emitted alongside it for smaller direct installs.

## Release procedure

1. Complete the version bump and release notes in reviewable commits.
2. Verify the command above and review the staged diff for secrets or unrelated files.
3. Push the tested commit to `master`.
4. Create and push the exact version tag, for example `git tag -a v0.2.0 -m "SubHub 0.2.0"` followed by `git push origin v0.2.0`.
5. `.github/workflows/release.yml` validates tag/version parity, tests and lints the app, signs all APKs, and publishes a GitHub release containing universal and per-ABI artifacts plus SHA-256 checksums.

Release signing is supplied only through the repository Actions secrets named in the workflow. Do not weaken signing or manufacture a different key for a later release; Android updates require the same key.
