# OpenTelemetry (experimental)

OpenTelemetry support is in an experimental state. To enable it, go to **Settings | Tools | Aspire** and enable
**Collect OpenTelemetry data** options.

## Metrics

The plugin can display a metrics table and chart for your dotnet projects. To show a chart, choose a metrics and
double-click on the row.

![Aspire metrics chart](metrics.jpg){ width="750" }

## Distributed Trace diagram

The plugin can display a distributed trace diagram. When you select the Aspire Host node in the Services tree, you will
see the diagram icon.

![Aspire distributed trace diagram](show-diagram.png){ width="750" }

This action opens a system diagram based on distributed traces.

![Aspire distributed trace diagram](diagram.png){ width="750" }

You can then select a single vertex, right-click on it, and explore additional attributes from the trace.

![Aspire distributed trace diagram](diagram-details.png){ width="750" }