# Build & Development Commands

Prerequisites: JDK 25, .NET SDK (auto-installed via `dotnet.cmd`).

```bash
./gradlew buildPlugin          # Build plugin ZIP → build/distributions/
./gradlew runIde               # Launch sandboxed Rider with the plugin
./gradlew check                # Full verification, including integration tests (agents need an explicit user request)
./gradlew test                 # All JVM tests, including integration tests (agents need an explicit user request)
./gradlew test --tests "com.jetbrains.aspire.unit.*" # JVM unit tests (safe for agents)
./gradlew prepareDotNetPart    # Generate RD protocol + SDK props (run before opening .slnx)
./dotnet.cmd build -c Release AspirePlugin.slnx   # Build .NET solution
```
