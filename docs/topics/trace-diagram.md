# Distributed Trace diagram (experimental)

The plugin can display a distributed trace diagram.
To enable it, go to **Settings | Tools | Aspire** and enable **Show projects in the Services tool window** and
**Collect OpenTelemetry data** options.

After that, when you select the Aspire Host node in the Services tree, you will see the diagram icon.

![Aspire distributed trace diagram](show-diagram.png){ width="750" }

This action opens a system diagram based on distributed traces.

![Aspire distributed trace diagram](diagram.png){ width="750" }

You can then select a single vertex, right-click on it, and explore additional attributes from the trace.

![Aspire distributed trace diagram](diagram-details.png){ width="750" }
