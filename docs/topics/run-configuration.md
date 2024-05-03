# Run Configuration

The plugin provides a special run configuration for the .NET Aspire Host projects.
It discovers such projects by the `<IsAspireHost>true</IsAspireHost>` MSBuild property.
For each `launchSettings.json` profile of this project, the corresponding launch configuration will be generated.

![Aspire Host run configuration](run-config.png){ width="700" }

{style="narrow"}
Project
: .NET Aspire host project (defined by the `IsAspireHost` property).

{style="narrow"}
Profile
: A profile name from the `launchSettings.json` file. After changing the profile, the `Environment Variables` and `URL`
fields will be updated accordingly.

Environment variables
: List of environment variables. The list is defined by the `environmentVariables` property from
the `launchSettings.json` file.

URL
: URL that will be opened after a successful launch.
By default, this value is filled with the `applicationUrl` property from the `launchSettings.json` file.

This run configuration allows you to run or debug multiple projects registered in the Host project.

![Debugging multiple Aspire projects](debugging.jpg){ width="700" }

> Usually, run configurations with the `.NET Launch Settings Profile` type will also be created for the Aspire Host
> project. By default, they will have the same name and a different icon. These can be useful if you don't want to debug
> your services and just run them as is (similar to the `dotnet run` experience). In this case, the plugin will not
> participate in the run and all its features will not be available.
>
{style="note"}

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