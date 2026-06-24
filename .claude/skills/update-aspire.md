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
   Use the Bash tool with `curl` to download the file directly — do not use WebFetch, as it will refuse to reproduce the raw file content verbatim:
   ```
   curl -fsSL "https://raw.githubusercontent.com/microsoft/aspire/refs/tags/v{VERSION}/src/Aspire.Hosting/Dashboard/proto/dashboard_service.proto" \
     -o src/dotnet/AspireWorker/Dashboard/dashboard_service.proto
   ```

4. **Update `aspire` util.**
   The `aspire` CLI is installed as a dotnet global tool and may not be on PATH. Use `~/.dotnet/tools/aspire update --self`. The command will report the current version; if it's already at the target version, that is fine.

5. **Run `aspire update` in test and sample projects.**
   List all immediate subdirectories in:
   - `testData/solutions/`
   - `sampleProjects/`

   For every subdirectory, `cd` into it and run `~/.dotnet/tools/aspire update --non-interactive --yes`.
   Note: `--non-interactive` requires `--yes` (`-y`) to be specified as well, otherwise the command fails.
   Run them sequentially (each command depends on shared NuGet state). Use a 10-minute total timeout.
   Some directories (e.g. pure ASP.NET Core, Worker, MAUI, or Azure Functions solutions) contain no Aspire AppHost and will report "no AppHost project files were detected" — this is expected and should be listed as "Skipped (no AppHost)" in the report, not as a failure.

6. **Report results.**
   Show the user a summary table listing:
   - CI template version: old → new
   - Each NuGet package: old version → new version
   - Proto file: updated or not
   - Each project directory: aspire update succeeded, skipped (no AppHost), or failed
