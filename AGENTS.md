# AGENTS.md

JetBrains Rider plugin for .NET Aspire — dual-architecture (Kotlin JVM + C# .NET worker) communicating over the RD protocol.

## Must-know rules

- Use `./dotnet.cmd` (not raw `dotnet`) so the SDK pinned in `global.json` is used.
- Run `./gradlew prepareDotNetPart` before opening `AspirePlugin.slnx` for the first time (or after protocol changes).
- **Never edit files under `**/generated/` or `**/Generated/`** — change the model in `protocol/` and regenerate.
- When making repository changes, update `CHANGELOG.md` with the corresponding changes.
- JVM integration tests under `src/test/kotlin/com/jetbrains/aspire/integration/` launch a full Rider test environment and are slow. Run them manually or on CI by default. Agents may run integration tests, `./gradlew test`, or `./gradlew check` only when the user explicitly requests the corresponding run; keep the run scoped to the requested tests. Agents may run unit tests with `./gradlew test --tests "com.jetbrains.aspire.unit.*"` without an explicit request.

## Detailed guidance

- [Build & commands](docs/agents/build.md)
- [Module & solution architecture](docs/agents/architecture.md)
- [RD protocol workflow](docs/agents/protocol.md)
- [Testing](docs/agents/testing.md)
- [Code conventions](docs/agents/conventions.md)
