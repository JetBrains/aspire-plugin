# RD Protocol

Models live in `protocol/src/main/kotlin/model/`. The `rdgen` Gradle task generates code into:

- Kotlin → `core/src/main/kotlin/com/jetbrains/aspire/generated/`
- C# → `src/dotnet/AspireWorker/Generated/` and `src/dotnet/AspirePlugin/Generated/`

**Never edit generated files directly.** Modify the model in `protocol/` and regenerate
(via `./gradlew prepareDotNetPart` or the **Generate Protocol** run configuration).
