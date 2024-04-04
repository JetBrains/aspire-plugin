# Run Configuration

The plugin provides a special run configuration for the .NET Aspire Host projects.
It discovers such projects by the `<IsAspireHost>true</IsAspireHost>` MSBuild property.

![Aspire Host run configuration](run-config.png){ width="700" }

{style="narrow"}
Project
: .NET Aspire host project (defined by the `IsAspireHost` property).

Environment variables
: List of environment variables. The list is defined by the `environmentVariables` property from
the `launchSettings.json` file.

URL
: URL that will be opened after a successful launch.
By default, this value is filled with the `applicationUrl` property from the `launchSettings.json` file.

This run configuration allows you to run or debug multiple projects registered in the Host project.

![Debugging multiple Aspire projects](debugging.jpg){ width="700" }

A separate node will also be created in the Services tool window. 
It allows you to monitor resources while Aspire Host is running.
For example, you can find resource properties, endpoints, environment variables, and read logs there.
In addition, the resource tree provides [OpenTelemetry information](open-telemetry.md) if this feature is enabled.

![Services tool window integration](services.png){ width="700" }

<seealso>
  <category ref="ext">
    <a href="https://www.jetbrains.com/help/rider/Run_Debug_Configuration.html">Rider run/debug configurations</a>
  </category>
</seealso>