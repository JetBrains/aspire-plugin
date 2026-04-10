---
name: changelog
description: Update CHANGELOG.md with a new release section from the latest GitHub release
user_invocable: true
---

# Update Changelog

Update `CHANGELOG.md` by cutting a new release section from the current `[Unreleased]` content.

## Steps

1. **Get the latest GitHub release.** Use `WebFetch` to fetch `https://github.com/JetBrains/aspire-plugin/releases` and extract the latest release tag name (this is the version, e.g. `2.4.2`) and the published date.

2. **Read `CHANGELOG.md`.**

3. **Check if this version already exists in the changelog.** If a `## [VERSION]` section already exists, stop and inform the user that the changelog is already up to date.

4. **Insert the new release section.** Take everything currently under `## [Unreleased]` (all subsections like `### Added`, `### Changed`, `### Fixed`, `### Removed` and their items) and move it under a new header:
   ```
   ## [VERSION] - YYYY-MM-DD
   ```
   Leave `## [Unreleased]` in place but empty (no subsections under it, just a blank line before the new release section).

5. **Update the reference links at the bottom of the file.** Find the existing `[Unreleased]` link and the line after it. Replace them so that:
   - `[Unreleased]` points to `compare/VERSION...HEAD`
   - A new line `[VERSION]` points to `compare/PREV_VERSION...VERSION`

   Where `PREV_VERSION` is the version that `[Unreleased]` previously compared against (the version in the old `[Unreleased]` compare link before `/...HEAD`).

   Example — before:
   ```
   [Unreleased]: https://github.com/JetBrains/aspire-plugin/compare/2.4.1...HEAD
   ```
   After (if new version is `2.4.2`):
   ```
   [Unreleased]: https://github.com/JetBrains/aspire-plugin/compare/2.4.2...HEAD
   [2.4.2]: https://github.com/JetBrains/aspire-plugin/compare/2.4.1...2.4.2
   ```

6. **Show the user a summary** of what changed: the version number, date, and the content that was moved from Unreleased.
