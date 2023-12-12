# Run Configuration

The plugin provides a special run configuration for the .NET Aspire Host project.
It discovers such a project by `<IsAspireHost>true</IsAspireHost>` MSBuild property.

![Aspire Host run configuration](run-config.jpg){ width="700" }

This run configuration allows you to run or debug multiple projects defined in the Host project.

![Debugging multiple Aspire projects](debugging.jpg){ width="700" }

The plugin will generate a separate .NET project run configuration for each of your projects and start them. By
viewing these configurations, you will be able to find out the exact parameters and environment variables that were used
for launching.

![Generated run configurations](generated-run-config.jpg){ width="500" }

<seealso>
  <category ref="ext">
    <a href="https://www.jetbrains.com/help/rider/Run_Debug_Configuration.html">Rider run/debug configurations</a>
  </category>
</seealso>