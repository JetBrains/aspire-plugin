# Repository Guidelines

## Project Structure & Module Organization
This repository is a Gradle multi-module plugin for JetBrains Rider. Root modules include `core`, `diagram`, `docker`, `database`, `protocol`, and `rider`; Kotlin sources live under each module’s `src/main/kotlin`. The .NET side lives in `src/dotnet`, with the main backend in `src/dotnet/AspirePlugin` and worker code in `src/dotnet/AspireWorker`. JVM-side tests are in `src/test/kotlin`, integration fixtures and golden files are in `testData`, and sample Aspire apps are in `sampleProjects`.

## Build, Test, and Development Commands
Use JDK 21 and the local wrapper.

- `./gradlew buildPlugin`: builds the plugin ZIP in `build/distributions` and publishes the .NET worker into the sandbox package.
- `./gradlew runIde`: builds and launches a sandboxed Rider instance for manual testing.
- `./gradlew check`: runs the default verification suite, including TestNG-based plugin tests.
- `./gradlew prepareDotNetPart`: generates RD protocol models and .NET SDK props before opening `AspirePlugin.slnx` in Rider.
- `./dotnet.cmd build -c Release AspirePlugin.slnx`: builds the .NET solution with the repo’s pinned SDK flow.

## Coding Style & Naming Conventions
Follow the existing Kotlin and C# style in the repository: 4-space indentation, braces on the same line, and descriptive PascalCase type names. Use `camelCase` for Kotlin members and local variables. Keep package names under `com.jetbrains.aspire...`. Treat `Generated` files and RD-generated models as output; regenerate them instead of editing by hand. Use the root `AspirePlugin.slnx.DotSettings` for Rider formatting defaults.

## Testing Guidelines
Add or update JVM tests in `src/test/kotlin/com/jetbrains/aspire` and keep test names behavior-focused, for example `ManifestGenerationTests` or `RunConfigurationTests`. Put new fixture solutions or golden files under the matching `testData/...` directory. Run `./gradlew check` before opening a PR; run `./gradlew test` for a faster local pass when you only need the JVM suite.

## Commit & Pull Request Guidelines
Recent commits use short, imperative subjects such as `Fix redis connection` and `Bump plugin version and update changelog`. Keep commit titles concise, capitalized, and focused on one change. PRs should explain user-visible impact, note affected modules, link related issues, and include screenshots or recordings when UI behavior changes. Call out regenerated files explicitly so reviewers can separate source edits from generated output.

## Generated Code & Environment Notes
`protocol` bridges the Kotlin and .NET parts; if you change shared models, regenerate before building. Prefer `dotnet.cmd` over a raw `dotnet` invocation so the correct SDK is resolved from `global.json`.
