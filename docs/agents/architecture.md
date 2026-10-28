# Module & Solution Architecture

## Gradle modules

| Module     | Purpose                                                          | Depends on |
|------------|------------------------------------------------------------------|------------|
| `protocol` | RD model definitions (Kotlin → generates both Kotlin + C#)       | —          |
| `shared`   | Shared models, service contracts, identifiers, and icons         | IntelliJ IDEA SDK |
| `core`     | Core plugin logic: embedded session servers, CLI, sessions, OTLP | `shared` + IntelliJ IDEA SDK |
| `core.frontend` | Settings UI, help, and dashboard actions shared by Services and Rider | `shared` + `core` |
| `services` | Aspire Services View UI and view models                          | `shared` + `core` + `core.frontend` + Services View plugin |
| `rider`    | Rider-specific: orchestration, debugging, manifests, launch cfg  | `shared` + `core` + `core.frontend` |
| `diagram`  | Architecture visualization (bundled Diagram plugin dependency)   | `shared`   |
| `docker`   | Docker container integration                                     | `core`     |
| `database` | Database connection support                                      | `core`     |
| `opentelemetry` | OpenTelemetry integration scaffolding (no services yet)      | `shared` + `core` + OpenTelemetry plugin |

`core.frontend` declares the `intellij.platform.frontend` dependency and separates common UI code from `core`.
It still calls local services in `core`; RPC and the remaining Split Mode integration are not configured yet.
The CLI run configuration and its editor remain in `core` until their UI and execution logic are separated.

## .NET projects (`src/dotnet/`)

- **AspirePlugin** — ReSharper/Rider backend plugin (DLL embedded in sandbox)
