# Testing

| Suite              | Location                                       | Framework |
|--------------------|------------------------------------------------|-----------|
| JVM tests          | `src/test/kotlin/com/jetbrains/aspire/`        | JUnit 5   |
| Test fixtures      | `testData/com/jetbrains/aspire/`               | —         |
| .NET integration   | `src/dotnet/AspireWorkerIntegrationTests/`     | —         |

JVM tests use:
- the `@Solution` annotation for fixture binding
- JUnit 5 Rider test bases such as `PerTestSolutionTestBase`

Run only JVM tests with `./gradlew test`; full verification with `./gradlew check`.
