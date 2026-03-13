# Releasing

This repository is not yet wired for automated publishing. For `v0.1.0`, keep the release process simple and manual.

## Suggested release flow

1. Confirm `CHANGELOG.md`, `VERSION`, and the language/package metadata all still agree on `0.1.0`.
2. Run the repo checks locally:
   - `python tools/check_metadata.py`
   - `./mvnw -q test`
   - the language-port test commands in `TESTING.md`
3. Merge the launch-readiness work to `main` and verify the GitHub Actions `ci` workflow is green.
4. Create and push tag `v0.1.0`.
5. Draft the GitHub Release notes from the `CHANGELOG.md` entry for `[0.1.0] - unreleased`, then replace `unreleased` with the release date in a follow-up documentation update.
