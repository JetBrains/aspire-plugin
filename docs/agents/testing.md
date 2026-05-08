# Testing

| Suite              | Location                                       | Framework |
|--------------------|------------------------------------------------|-----------|
| JVM tests          | `src/test/kotlin/com/jetbrains/aspire/`        | TestNG    |
| Test fixtures      | `testData/com/jetbrains/aspire/`               | —         |
| .NET integration   | `src/dotnet/AspireWorkerIntegrationTests/`     | —         |

JVM tests use:
- the `@Solution` annotation for fixture binding
- `PerTestSolutionTestBase` as the base class

Run only JVM tests with `./gradlew test`; full verification with `./gradlew check`.
