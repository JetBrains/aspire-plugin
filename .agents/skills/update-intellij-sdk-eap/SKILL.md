---
name: update-intellij-sdk-eap
description: Update this Aspire plugin to the newest Rider and IntelliJ IDEA EAPs for a user-selected release line, then build and repair clear SDK-upgrade regressions.
user_invocable: true
---

# Update IntelliJ SDK EAP

Update the IntelliJ Platform SDK versions in this repository only when the user has selected the target EAP release line.

## Input

Before making a network request, editing a file, or starting a build, ask the user for the target EAP release line in the exact form `YYYY.N`, for example `2026.3`. Do not infer the release line or proceed without this input.

## Steps

1. Read the current `ideaVersion`, `riderVersion`, and `pluginVerificationIdeVersion` from `gradle.properties`. Preserve unrelated working-tree changes.

2. Fetch and parse `https://jb.gg/intellij-platform-builds-list` as JSON. Find the `RD` product and select the first release whose `type` is `eap` and whose `version` matches the requested release line followed by `-EAPM`, where `M` is the EAP number. For example, `2026.3` matches `2026.3-EAP3`. The list is newest-first, so this is the newest Rider EAP for the requested release line. If no such release exists, stop and report it; do not substitute another release line.

3. Find the `IIU` product and select the first release whose `type` is `eap` and whose `version` exactly equals the requested release line. The list is newest-first, so this is the newest IntelliJ IDEA EAP for that release line. If no such release exists, stop and report it without making edits.

4. Update only these properties in `gradle.properties`:
   - Set `ideaVersion` to the selected `IIU` release's `build`.
   - Set `riderVersion` to the selected `RD` release's `version` followed by `-SNAPSHOT`.
   - Set `pluginVerificationIdeVersion` to the same Rider value.

5. Run the existing **Build Plugin** run configuration, defined in `.run/Build Plugin.run.xml`. It invokes Gradle task `buildPlugin`. If direct IDE run-configuration execution is unavailable, run the equivalent command from the repository root:
   ```powershell
   ./gradlew buildPlugin
   ```
   Do not run `test`, `check`, or JVM integration tests for this workflow.

6. When the build fails because of the SDK update, inspect the actionable Gradle/compiler error and make only a confident, minimal compatibility fix. Do not edit generated sources, weaken or mute validation, downgrade the selected SDK, or change unrelated code. Re-run **Build Plugin** after each fix.

7. If the cause or fix is not immediately clear, stop instead of guessing. Report the requested release line, selected Rider EAP and IDEA build, property changes, build result, exact error location/output, and any fixes already attempted.

## Completion

On success, report the previous and new values of all three properties, the selected upstream releases, the **Build Plugin** result, and the files changed. Run `git diff --check` before reporting completion.
