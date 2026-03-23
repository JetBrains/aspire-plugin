# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JetBrains Rider plugin for .NET Aspire support. Dual-architecture: Kotlin (JVM/IDE side) and C# (.NET backend/worker side), communicating via the RD protocol.

## Build & Development Commands

Prerequisites: JDK 21, .NET SDK (auto-installed via `dotnet.cmd`).

```bash
./gradlew buildPlugin          # Build plugin ZIP → build/distributions/
./gradlew runIde               # Launch sandboxed Rider with the plugin
./gradlew check                # Full verification (TestNG tests + plugin structure)
./gradlew test                 # JVM tests only (faster)
./gradlew prepareDotNetPart    # Generate RD protocol + SDK props (run before opening .slnx)
./dotnet.cmd build -c Release AspirePlugin.slnx   # Build .NET solution
```

Use `dotnet.cmd` instead of raw `dotnet` to ensure correct SDK resolution from `global.json`.

IDE run configurations: "Run Plugin" (debug JVM side), "Generate Protocol" (regenerate RD models).

## Module Structure

Root project (`intellij.aspire`) bundles all modules:

| Module | Purpose | Depends on |
|--------|---------|------------|
| `protocol` | RD model definitions (Kotlin → generates both Kotlin + C#) | — |
| `core` | Core plugin logic: sessions, dashboard, OTLP, worker communication | Rider SDK |
| `rider` | Rider-specific: orchestration, debugging, manifests, launch profiles | `core` |
| `diagram` | Architecture visualization (bundled Diagram plugin dependency) | `core` |
| `docker` | Docker container integration | `core` |
| `database` | Database connection support | `core` |

.NET projects in `src/dotnet/`:
- **AspirePlugin** — ReSharper/Rider backend plugin (DLL embedded in sandbox)
- **AspireWorker** — Background worker (gRPC-based, published as standalone app)
- **AspireWorkerIntegrationTests** — .NET-side integration tests

## RD Protocol

Protocol models are defined in `protocol/src/main/kotlin/model/`. The `rdgen` task generates:
- Kotlin → `core/src/main/kotlin/com/jetbrains/aspire/generated/`
- C# → `src/dotnet/AspireWorker/Generated/` and `src/dotnet/AspirePlugin/Generated/`

**Never edit generated files directly.** Change the model in `protocol/` and regenerate.

## Testing

- JVM tests: `src/test/kotlin/com/jetbrains/aspire/` using TestNG
- Test fixtures/golden files: `testData/com/jetbrains/aspire/`
- .NET integration tests: `src/dotnet/AspireWorkerIntegrationTests/`
- Tests use `@Solution` annotation for fixture binding and `PerTestSolutionTestBase` base class

## Code Conventions

- Kotlin: 4-space indent, `camelCase` members, packages under `com.jetbrains.aspire`
- C#: 4-space indent, `PascalCase` types, formatting via `AspirePlugin.slnx.DotSettings`
- Kotlin compiler flag `-Xcontext-parameters` is enabled
- Commits: short imperative subjects (e.g., "Fix redis connection")
