---
name: update-aspire
description: Update Aspire version across CI, NuGet constants, proto file, and all test/sample projects
user_invocable: true
---

# Update Aspire Version

Performs a full Aspire version bump: CI templates, NuGet package constants, upstream proto file, and all test/sample projects.

## Input

The user must provide the target Aspire version (e.g. `9.3.0`). If not provided as an argument, ask for it.

## Steps

1. **Update CI template version.**
   Edit `.github/workflows/build.yml`. Find the line `dotnet new install Aspire.ProjectTemplates@...` and replace the version with the target version.

2. **Update NuGet package versions.**
   Edit `rider/src/main/kotlin/com/jetbrains/aspire/rider/orchestration/NuGetPackages.kt`.
   For each package listed in the file, look up its latest version on nuget.org (use WebSearch or WebFetch to check `https://www.nuget.org/packages/{PACKAGE_NAME}`). Update the version constants accordingly. Note: some packages (e.g. `Aspire.Hosting.Maui`) may only have preview versions — use the latest available.

3. **Fetch upstream proto file.**
   Use WebFetch to download the proto file from the Aspire repo at the matching release tag:
   `https://raw.githubusercontent.com/microsoft/aspire/refs/tags/v{VERSION}/src/Aspire.Hosting/Dashboard/proto/dashboard_service.proto`
   Overwrite `src/dotnet/AspireWorker/Dashboard/dashboard_service.proto` with the fetched content using the Write tool.

4. **Update `aspire` util.** 
   Run `aspire update --self`.

5. **Run `aspire update` in test and sample projects.**
   List all immediate subdirectories in:
   - `testData/solutions/`
   - `sampleProjects/`

   For every subdirectory, run `aspire update --non-interactive` using the Bash tool with a 5-minute timeout. Run them sequentially (each command depends on shared NuGet state).

6. **Report results.**
   Show the user a summary table listing:
   - CI template version: old → new
   - Each NuGet package: old version → new version
   - Proto file: updated or not
   - Each project directory: aspire update succeeded or failed
