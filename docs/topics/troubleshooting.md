# Troubleshooting

If you experience a potential bug, you can report it in the
plugin's [Issues section](https://github.com/rafaelldi/aspire-plugin/issues) on GitHub.

## IDE logs

You can collect and attach logs, they will help us investigate the problem. To do that:

1. Open **Help | Diagnostics Tools | Choose Trace Scenarios** menu.
2. Press **Advanced log settings** button.
3. Insert `me.rafaelldi.aspire:all` into the text area.
4. Reproduce the issue.
5. Press **Help | Collect Logs and Diagnostic Data**.
6. Attach archive to the issue.

![Plugin diagnostics scenario](diagnostics.jpg){ width="600" }

## Aspire session host logs

Sometimes it is required to collect logs from `aspire-session-host` service. This component communicates with the Aspire
host. To do this, go to
the [plugin directory](https://www.jetbrains.com/help/rider/Directories_Used_by_the_IDE_to_Store_Settings_Caches_Plugins_and_Logs.html#plugins-directory).
Then open the `aspire-plugin/aspire-session-host/appsettings.json` file. This is a default
aspnetcore [configuration file](https://learn.microsoft.com/en-us/aspnet/core/fundamentals/logging/?view=aspnetcore-8.0#configure-logging).
Then change the level of the **Default** and **AspireSessionHost** categories. The updated logs will be reported to the
IDE logs.

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "AspireSessionHost": "Trace"
    }
  },
  "AllowedHosts": "*"
}
```