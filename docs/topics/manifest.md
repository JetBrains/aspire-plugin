# Manifest Generation

The plugin allows you to generate an Aspire deployment manifest. This file describes your project and can be used by
different tools to deploy the project, for example,
to [Azure](https://learn.microsoft.com/en-us/dotnet/aspire/deployment/azure/aca-deployment)
or [Kubernetes](https://github.com/prom3theu5/aspirational-manifests).

To do this, you can select the Aspire Host node in the Services tool window and run the **Generate Aspire Manifest**
action.



Otherwise, select the Aspire Host project in the Solution view and run this action from the **Tools | Generate Aspire
Manifest** menu.



The manifest will be generated under the Aspire Host project in the `aspire-manifest` folder.

<seealso>
  <category ref="ext">
    <a href="https://learn.microsoft.com/en-us/dotnet/aspire/deployment/overview#deployment-manifest">Deployment manifest</a>
    <a href="https://github.com/prom3theu5/aspirational-manifests">Aspirate (Aspir8)</a>
  </category>
</seealso>