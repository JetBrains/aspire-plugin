# Testing

| Suite              | Location                                                   | Framework |
|--------------------|------------------------------------------------------------|-----------|
| JVM unit           | `src/test/kotlin/com/jetbrains/aspire/unit/`                | JUnit 5   |
| JVM integration    | `src/test/kotlin/com/jetbrains/aspire/integration/`         | JUnit 5   |
| Gold files         | `testData/com/jetbrains/aspire/`                           | —         |
| Solution fixtures  | `testData/solutions/`                                       | —         |
| .NET integration   | `src/dotnet/AspireWorkerIntegrationTests/`                 | —         |

JVM integration tests use:

- the `@Solution` annotation for fixture binding
- JUnit 5 Rider test bases such as `PerTestSolutionTestBase`

JVM integration tests launch a full Rider test environment and are slow. Run them only manually or on CI; agents must not run them.

- Unit tests: `./gradlew test --tests "com.jetbrains.aspire.unit.*"`
- Integration tests: `./gradlew test --tests "com.jetbrains.aspire.integration.*"`
- All JVM tests: `./gradlew test`
- Full verification: `./gradlew check`
