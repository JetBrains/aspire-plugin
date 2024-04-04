# OpenTelemetry (experimental)

OpenTelemetry support is in an experimental state. To enable it, go to **Settings | Tools | Aspire** and enable
**Collect OpenTelemetry data** option.

## Metrics

The plugin can display a metrics table and chart for your dotnet projects. To show a chart, choose a metrics and
double-click on the row.

![Metrics chart](metrics.jpg){ width="700" }

## Distributed Trace diagram

The plugin can display a distributed trace diagram. When you select the Aspire Host node in the Services tree, you will
see the diagram icon.

![Distributed trace diagram action](show-diagram.png){ width="400" }

This action opens a system diagram based on distributed traces.

![Distributed trace diagram](diagram.png){ width="500" }

You can then select a single vertex, right-click on it, and explore additional attributes from the trace.

![Distributed trace diagram with details](diagram-details.png){ width="500" }

<seealso>
  <category ref="ext">
    <a href="https://opentelemetry.io/">OpenTelemetry</a>
  </category>
</seealso>