# Module & Solution Architecture

## Gradle modules

| Module     | Purpose                                                          | Depends on |
|------------|------------------------------------------------------------------|------------|
| `protocol` | RD model definitions (Kotlin → generates both Kotlin + C#)       | —          |
| `core`     | Core plugin logic: sessions, dashboard, OTLP, worker comms       | Rider SDK  |
| `rider`    | Rider-specific: orchestration, debugging, manifests, launch cfg  | `core`     |
| `diagram`  | Architecture visualization (bundled Diagram plugin dependency)   | `core`     |
| `docker`   | Docker container integration                                     | `core`     |
| `database` | Database connection support                                      | `core`     |

## .NET projects (`src/dotnet/`)

- **AspirePlugin** — ReSharper/Rider backend plugin (DLL embedded in sandbox)
- **AspireWorker** — Background worker (gRPC-based, published as standalone app)
- **AspireWorkerIntegrationTests** — .NET-side integration tests