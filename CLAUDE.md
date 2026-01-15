# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a JetBrains Rider plugin that provides support for .NET Aspire projects. The plugin is built using Kotlin for
the IntelliJ Platform frontend and C# for the ReSharper backend, communicating via JetBrains RD (Reactive Distributed)
protocol.

## Common Commands

### Build and Run

```bash
# Build the plugin
./gradlew buildPlugin

# Compile .NET components only
./gradlew compileDotNet

# Regenerate protocol models after changing protocol/src/main/kotlin/model/*
./gradlew rdgen

# Run tests
./gradlew check

# Run IDE with the plugin in sandbox mode
./gradlew runIde
```

### Development Workflow

Same results can be achieved by running the existing IntelliJ run configurations:

- `Build Plugin` to build the plugin
- `Generate Protocol` to regenerate protocol models
- `Run Tests` to run the plugin tests
- `Run Plugin` to run the plugin in sandbox mode

### Testing

- Kotlin tests: test suite uses TestNG. Tests are located in `src/test/kotlin` directories. Tests verify the behavior
  of the plugin itself. Test data solutions are in `testData/solutions/` and represent various Aspire and .NET projects.
- .NET tests: test suite uses xUnit. Tests are located in `src/dotnet/AspireWorkerIntegrationTests/`. Tests verify the
  behavior of Aspire Worker and communication between it, Aspire DCP, and the plugin.

## Architecture

Plugin consists of a Frontend part (Kotlin), a Backend part (C#), and an Aspire Worker (C#).

- The Frontend part acts as an IntelliJ plugin and is based on the IntelliJ Platform
SDK ([docs](https://plugins.jetbrains.com/docs/intellij/welcome.html)).
- The Backend part acts as a ReSharper plugin and is based on the ReSharper
SDK ([docs](https://www.jetbrains.com/help/resharper/sdk/welcome.html)).
- The Aspire Worker is a standalone ASP.NET Core application that connects the plugin to the Aspire
DCP ([IDE session endpoint requests](https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md)).

### Frontend part Structure

The Frontend part is organized into six Gradle modules:

1. **`:protocol`** - RD protocol definitions that generate communication models for both Kotlin and C# sides
2. **`:core`** - Platform-agnostic core functionality (AspireWorker management, resource models, dashboard UI)
3. **`:rider`** - Rider-specific implementations (run configurations, debugger integration, project orchestration)
4. **`:database`** - Automatic database connection management
5. **`:diagram`** - Resource relationship visualization
6. **`:docker`** - Docker integration

### Communication Architecture

The plugin uses a three-layer communication architecture:

```
IntelliJ/Rider Frontend (Kotlin)
    ↕ RD Protocol (AspirePluginModel)
ReSharper Backend (C# - AspirePlugin.dll)

IntelliJ/Rider Frontend (Kotlin)
    ↕ RD Protocol (AspireWorkerModel)
AspireWorker Process (C# - AspireWorker.dll)
    ↕ HTTP and gRPC
Aspire DCP (Developer Control Plane)
```

**AspirePluginModel**: Communication between Rider frontend and ReSharper backend for IDE-specific operations (project
detection, adding references, unit test integration).

**AspireWorkerModel**: Communication with the standalone AspireWorker process that bridges between the plugin and
Aspire's DCP. The worker watches resources, streams logs, and manages session lifecycle.

### Protocol Generation

Protocol models are defined in:

- `protocol/src/main/kotlin/model/aspirePlugin/AspirePluginModel.kt`
- `protocol/src/main/kotlin/model/aspireWorker/AspireWorkerModel.kt`

RdGen generates matching code in:

- Kotlin: `core/src/main/kotlin/com/jetbrains/aspire/generated/` and
  `rider/src/main/kotlin/com/jetbrains/aspire/rider/generated/`
- C#: `src/dotnet/AspireWorker/Generated/` and `src/dotnet/AspirePlugin/Generated/`

After modifying protocol definitions, run the "Generate Protocol" run configuration or `:protocol:rdgen` Gradle task.

### Key Components

**AspireWorker (Kotlin - core module)**

- Service that manages the AspireWorker.exe process lifecycle
- Creates RD protocol server via SocketWire
- Maintains a map of AspireAppHost models

**AspireWorker (C# - AspireWorker project)**

- Standalone ASP.NET Core application launched as an external process
- Connects to Aspire's Dashboard Service via gRPC
- Watches resources and logs using streaming RPCs
- Handles session create/delete requests from DCP

**AspireAppHost**

- Represents a running Aspire AppHost project
- Manages resources (containers, projects, executables, databases)
- Handles session requests (start/stop/restart)

**SessionManager**

- Handles the requests from Aspire DCP to start/stop sessions
- Batches start/stop requests within a 1-second window
- Optimizes builds by grouping multiple requests
- Routes requests to appropriate handlers (DotNet, etc.)

## Development Tips

### Working with Kotlin Side

Open the root folder in IntelliJ IDEA. The main plugin module is the root project. Use the "Run Plugin" run
configuration to launch Rider with the plugin.

### Working with C# Side

Open `AspirePlugin.slnx` in Rider. Before opening, run `./gradlew prepareDotnetPart` to generate protocol models and
NuGet config. The C# solution includes:

- `AspirePlugin` - ReSharper backend plugin
- `AspireWorker` - Standalone worker process
- `AspireWorkerIntegrationTests` - Integration tests

## Important Files

- `build.gradle.kts` - Main build configuration
- `gradle.properties` - Plugin version, platform version, build configuration
- `nuget.config` - Auto-generated, points to Rider SDK NuGet packages
- `protocol/build.gradle.kts` - RdGen configuration
- `src/dotnet/AspirePlugin/AspirePlugin.csproj` - ReSharper backend
- `src/dotnet/AspireWorker/AspireWorker.csproj` - Aspire Worker process
