using System;
using System.Linq;
using JetBrains.Application.I18n;
using JetBrains.Application.Parts;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.Rider.Aspire.Plugin.Generated;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class AspireResourceProtocolHost
{
    private readonly AspirePluginModel myModel;
    private readonly IDaemon myDaemon;

    public AspireResourceProtocolHost(Lifetime lifetime, ISolution solution, IDaemon daemon)
    {
        myModel = solution.GetProtocolSolution().GetAspirePluginModel();
        myDaemon = daemon;

        myModel.Resources.View(lifetime, (resourceLifetime, _, _) =>
        {
            InvalidateDaemon();
            resourceLifetime.OnTermination(InvalidateDaemon);
        });
    }

    public AspireRdResource? FindResource(string declarationResourceName)
    {
        var byDisplayName = myModel.Resources.Values.FirstOrDefault(resource =>
            string.Equals(resource.DisplayName, declarationResourceName, StringComparison.Ordinal));
        return byDisplayName ?? myModel.Resources.Values.FirstOrDefault(resource =>
            string.Equals(resource.Name, declarationResourceName, StringComparison.Ordinal));
    }

    public void ExecuteResourceCommand(string resourceName, string commandName)
    {
        myModel.ExecuteResourceCommand(new ExecuteResourceCommandRequest(resourceName, commandName));
    }

    private void InvalidateDaemon()
    {
        myDaemon.Invalidate("AspireResourceProtocolHost.ResourcesChanged".NON_LOCALIZABLE());
    }
}
