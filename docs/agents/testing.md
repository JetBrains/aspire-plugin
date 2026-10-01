# Testing

| Suite              | Location                                                   | Framework |
|--------------------|------------------------------------------------------------|-----------|
| JVM unit           | `src/test/kotlin/com/jetbrains/aspire/unit/`                | JUnit 5   |
| JVM integration    | `src/test/kotlin/com/jetbrains/aspire/integration/`         | JUnit 5   |
| Gold files         | `testData/com/jetbrains/aspire/`                           | —         |
| Solution fixtures  | `testData/solutions/`                                       | —         |

JVM integration tests use:

- the `@Solution` annotation for fixture binding
- JUnit 5 Rider test bases such as `PerTestSolutionTestBase`

JVM integration tests launch a full Rider test environment and are slow. Run them manually or on CI by default. Agents may run integration tests or the full JVM suite only when the user explicitly requests the corresponding run; keep the run scoped to the requested tests. The `update-aspire` skill also permits relevant integration tests as part of an Aspire update, using explicit test filters.

- Unit tests: `./gradlew test --tests "com.jetbrains.aspire.unit.*"`
- Integration tests: `./gradlew :test --tests "com.jetbrains.aspire.integration.*"`
- All JVM tests: `./gradlew test`
- Full verification: `./gradlew check`

Use the root `:test` task when filtering integration tests. The unqualified `test` task also runs tests in subprojects, where the integration test filter matches no tests.

The MAUI orchestration test needs the `maui-aspire-servicedefaults` template (`MauiAspire.ServiceDefaults.CSharp`), which is provided by `Microsoft.Maui.Templates.net10`, in addition to `Aspire.ProjectTemplates`. CI pins the template package to `10.0.20` to match the gold file. Install the same version locally with `./dotnet.cmd new install Microsoft.Maui.Templates.net10@10.0.20 --force`, then check that the template is available with `./dotnet.cmd new list maui-aspire-servicedefaults`. Install the package in the .NET environment Rider uses, even if the MAUI workload is installed in another .NET installation.
