# Build & Development Commands

Prerequisites: JDK 25, .NET SDK (auto-installed via `dotnet.cmd`).

```bash
./gradlew buildPlugin          # Build plugin ZIP → build/distributions/
./gradlew runIde               # Launch sandboxed Rider with the plugin
./gradlew check                # Full verification (TestNG tests + plugin structure)
./gradlew test                 # JVM tests only (faster)
./gradlew prepareDotNetPart    # Generate RD protocol + SDK props (run before opening .slnx)
./dotnet.cmd build -c Release AspirePlugin.slnx   # Build .NET solution
```