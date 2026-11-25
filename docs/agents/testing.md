# Testing

| Suite             | Location                                            | Framework |
|-------------------|-----------------------------------------------------|-----------|
| JVM unit          | `src/test/kotlin/com/jetbrains/aspire/unit/`        | JUnit 6   |
| JVM integration   | `src/test/kotlin/com/jetbrains/aspire/integration/` | JUnit 6   |
| Gold files        | `testData/com/jetbrains/aspire/`                    | —         |
| Solution fixtures | `testData/solutions/`                               | —         |

## Writing tests

- Use Arrange–Act–Assert without `// Arrange`-style comments; check one behavior per test where possible.
- Put test objects in separate variables instead of constructing them inside method calls.
- Integration tests bind fixtures with `@Solution` and use JUnit Jupiter Rider bases such as `PerTestSolutionTestBase`.
- For a substantial user-visible feature or behavior change, add or extend a test. Use a unit test if the logic can
  be isolated from Rider; use an integration test if the behavior is observable only end to end (an action on a
  solution, generated run configurations, running or debugging an AppHost). Reuse existing test classes and
  fixtures where possible. Small fixes and refactorings don't need new tests. If a test isn't feasible, say why in
  the final report.
- Review a changed gold file as a behavior change; never update it only to make a test pass.

## Choosing tests to run

Run only tests related to the change:

- **Unit:** after every non-trivial change. Test packages mirror production ones (`core/.../worker/dcp` →
  `unit/worker/dcp`); also search `src/test` for the changed type names.
- **Integration:** when the change affects a flow below, without waiting for a user request, filtered to the
  affected classes or methods. Each class takes minutes.
- **Full suite** (`./gradlew test` or `check`): only on explicit user request.

| Changed functionality                                                                   | Integration test class             |
|-----------------------------------------------------------------------------------------|------------------------------------|
| Adding Aspire orchestration (`rider/orchestration`, `AddAspireTo*Action`)               | `AspireOrchestrationTests`         |
| AppHost detection, run configuration generation (`rider/run`, `rider/launchProfiles`)   | `RunConfigurationTests`            |
| Running an AppHost (`core/dcp`, `core/sessions`, `rider/sessions`, `rider/processLaunchers`, `rider/run/host`) | `RunningApplicationTests`          |
| File-based AppHost (`rider/run/file`)                                                   | `RunningFileBasedApplicationTests` |
| Debugging (`rider/debugger`, debug paths in sessions and launchers)                     | `DebuggingApplicationTests`        |
| Aspire test projects (`rider/unitTests`, `src/dotnet/AspirePlugin/UnitTest`)            | `TestingApplicationTests`          |
| Publishing an AppHost (`PublishAppHostAction`)                                          | `PublishAppHostActionTest`         |

For `src/dotnet/` and `protocol/` changes, run the integration classes of the flows that use the changed code.
If the environment blocks a test (missing SDK, workload, or template), report it as blocked; ask before installing
global .NET templates or workloads.

## Running tests

Always use the root `:test` task with `--tests` filters (the unqualified `test` also runs subprojects, where the
filters match nothing). A filter can be a package wildcard, a class, or a method; names with spaces work as is,
`*` matches any part, and `--tests` can be repeated:

```bash
./gradlew :test --tests "com.jetbrains.aspire.unit.worker.dcp.*"
./gradlew :test --tests "com.jetbrains.aspire.integration.RunningApplicationTests.Running default aspire solution launches dashboard"
```

Test output is huge, so don't read it in the console. Disable it, redirect the log, and read only the summary:

```bash
./gradlew :test --tests "<filter>" -PtestShowStandardStreams=false > build/agent-test.log 2>&1; echo "exit=$?"
grep -nE "FAILED$|tests completed|What went wrong|No tests found|BUILD (SUCCESSFUL|FAILED)" build/agent-test.log
```

- Each failure is a `<Class> > <method>() FAILED` line followed by the stack trace (`grep -A 30 "FAILED$"`).
  Compilation errors are under `What went wrong`.
- Counts per class: `grep -ohE '<testsuite name="[^"]*" tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+"'
  build/test-results/test/*.xml`. These files also hold failure details and captured output.
- For integration failures, grep `idea.log` and `backend*.log` in
  `.intellijPlatform/sandbox/intellij.aspire/RD-<version>/log-test/` instead of reading them in full.
- Run integration tests in the background with a timeout of at least 30 minutes and wait for the notification.
- A first run in a new worktree compiles everything (several minutes); later unit class runs take about 15 seconds.
- `UP-TO-DATE` or `FROM-CACHE` for `test` means results from identical inputs are reused; add `--rerun` to force a
  run. Add `--fail-fast` to stop at the first failure. Don't add `clean` or `--no-daemon`.

## Environment prerequisites

The MAUI orchestration test needs the `maui-aspire-servicedefaults` template from
`Microsoft.Maui.Templates.net11` (pinned to `11.0.0` on CI to match the gold file) in addition to
`Aspire.ProjectTemplates`. Install it in the .NET environment Rider uses, even if the MAUI workload is in another
installation: `./dotnet.cmd new install Microsoft.Maui.Templates.net11@11.0.0 --force`, then check with
`./dotnet.cmd new list maui-aspire-servicedefaults`.
