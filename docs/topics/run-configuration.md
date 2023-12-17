# Run Configuration

The plugin provides a special run configuration for the .NET Aspire Host projects.
It discovers such projects by the `<IsAspireHost>true</IsAspireHost>` MSBuild property.

![Aspire Host run configuration](run-config.jpg){ width="700" }

{style="narrow"}
Project
: .NET Aspire host project (defined by the `IsAspireHost` property).

URL
: URL that will be opened after a successful launch.
By default, this value is filled with the `applicationUrl` property from the `launchSettings.json` file.

This run configuration allows you to run or debug multiple projects registered in the Host project.

![Debugging multiple Aspire projects](debugging.jpg){ width="700" }

The plugin will generate a separate `.NET Project` run configuration for each of your projects and start them.
By viewing these configurations, you will be able to find out the exact parameters and environment variables that were
used for launching.

![Generated run configurations](generated-run-config.jpg){ width="500" }

<seealso>
  <category ref="ext">
    <a href="https://www.jetbrains.com/help/rider/Run_Debug_Configuration.html">Rider run/debug configurations</a>
  </category>
</seealso>