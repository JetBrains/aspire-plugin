# Module & Solution Architecture

## Gradle modules

| Module     | Purpose                                                          | Depends on |
|------------|------------------------------------------------------------------|------------|
| `protocol` | RD model definitions (Kotlin → generates both Kotlin + C#)       | —          |
| `core`     | Core plugin logic: embedded session servers, actions, OTLP      | `shared` + IntelliJ IDEA SDK |
| `services` | Aspire Services View UI and view models                          | `core` + Services View plugin |
| `rider`    | Rider-specific: orchestration, debugging, manifests, launch cfg  | `core`     |
| `diagram`  | Architecture visualization (bundled Diagram plugin dependency)   | `shared`   |
| `docker`   | Docker container integration                                     | `core`     |
| `database` | Database connection support                                      | `core`     |
| `opentelemetry` | OpenTelemetry integration scaffolding (no services yet)      | `shared` + `core` + OpenTelemetry plugin |

## .NET projects (`src/dotnet/`)

- **AspirePlugin** — ReSharper/Rider backend plugin (DLL embedded in sandbox)
