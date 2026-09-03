---
name: update-aspire
description: Update Aspire version across CI, NuGet constants, proto file, and all test/sample projects
user_invocable: true
---

# Update Aspire Version

Performs a full Aspire version bump: CI templates, NuGet package constants, upstream proto file, and all test/sample projects.

## Input

The user must provide the target Aspire version in full semver form (e.g. `13.3.0`). If not provided as an argument, ask for it.

## Steps

1. **Update CI template version.**
   Edit `.github/workflows/build.yml`. Find the line `dotnet new install Aspire.ProjectTemplates@...` and replace the version with the target version.

2. **Update NuGet package versions.**
   Edit `rider/src/main/kotlin/com/jetbrains/aspire/rider/orchestration/NuGetPackages.kt`.
   For each package listed in the file, look up its latest version on nuget.org (use WebSearch or WebFetch to check `https://www.nuget.org/packages/{PACKAGE_NAME}`). Update the version constants accordingly. Note: some packages (e.g. `Aspire.Hosting.Maui`) may only have preview versions — use the latest available.

3. **Fetch upstream proto file.**
   The tag format is `v{VERSION}` using the full semver (e.g. `v13.3.0`, not `v13.3`).
   The checked-in destination is `core/src/main/protos/dashboard_service.proto`.

   On Windows, invoke `curl.exe` directly.
   Use `curl` to download the file directly — do not use WebFetch, as it will refuse to reproduce the raw file content verbatim:
   ```
   curl.exe -fsSL "https://raw.githubusercontent.com/microsoft/aspire/refs/tags/v{VERSION}/src/Aspire.Hosting/Dashboard/proto/dashboard_service.proto" \
     -o core/src/main/protos/dashboard_service.proto
   ```

4. **Update the `aspire` CLI.**
   Do not assume Aspire is registered as a dotnet global tool or installed at `~/.dotnet/tools/aspire`. On Windows, locate it with `Get-Command aspire`; it may resolve to `C:\\Users\\<user>\\.aspire\\bin\\aspire.exe` even when `./dotnet.cmd tool list --global` does not list Aspire.

   Check the installed version with `aspire --version`, then update it without a channel-selection prompt:
   ```
   aspire update --self --channel stable --non-interactive
   ```
   The direct `aspire update --self` command can fail in a non-interactive terminal because it attempts to show that prompt.

5. **Run `aspire update` in test and sample projects.**
   List all immediate subdirectories in:
   - `testData/solutions/`
   - `sampleProjects/`

   For every subdirectory, change into it and run `aspire update --non-interactive --yes`.
   Note: `--non-interactive` requires `--yes` (`-y`) to be specified as well, otherwise the command fails.
   Run them sequentially (each command depends on shared NuGet state). Use a 10-minute total timeout. If the command runner has a shorter per-command limit, split the sequential sweep into smaller batches; do not run projects concurrently.
   Some directories (e.g. pure ASP.NET Core, Worker, MAUI, or Azure Functions solutions) contain no Aspire AppHost and will report "no AppHost project files were detected" — this is expected and should be listed as "Skipped (no AppHost)" in the report, not as a failure.

   CLI wording for no-AppHost projects varies by version, so do not depend on one exact message string when classifying a skipped result.

6. **Report results.**
   Show the user a summary table listing:
   - CI template version: old → new
   - Each NuGet package: old version → new version
   - Proto file: updated or not
   - Each project directory: aspire update succeeded, skipped (no AppHost), or failed
