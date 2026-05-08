# AGENTS.md

JetBrains Rider plugin for .NET Aspire — dual-architecture (Kotlin JVM + C# .NET worker) communicating over the RD protocol.

## Must-know rules

- Use `./dotnet.cmd` (not raw `dotnet`) so the SDK pinned in `global.json` is used.
- Run `./gradlew prepareDotNetPart` before opening `AspirePlugin.slnx` for the first time (or after protocol changes).
- **Never edit files under `**/generated/` or `**/Generated/`** — change the model in `protocol/` and regenerate.

## Detailed guidance

- [Build & commands](docs/agents/build.md)
- [Module & solution architecture](docs/agents/architecture.md)
- [RD protocol workflow](docs/agents/protocol.md)
- [Testing](docs/agents/testing.md)
- [Code conventions](docs/agents/conventions.md)