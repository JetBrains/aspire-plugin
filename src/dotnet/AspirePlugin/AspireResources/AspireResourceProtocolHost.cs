using JetBrains.Application.I18n;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Collections.Viewable;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.RdBackend.Common.Features.Documents;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.Rider.Aspire.Plugin.Generated;

namespace JetBrains.Rider.Aspire.Plugin.AspireResources;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class AspireResourceProtocolHost
{
    private readonly AspirePluginModel _model;
    private readonly ISolution _solution;
    private readonly IDaemon _daemon;

    public AspireResourceProtocolHost(Lifetime lifetime, ISolution solution, IDaemon daemon)
    {
        _solution = solution;
        _model = solution.GetProtocolSolution().GetAspirePluginModel();
        _daemon = daemon;

        _model.Resources.View(lifetime, (resourceLifetime, _, _) =>
        {
            InvalidateDaemon();
            resourceLifetime.OnTermination(InvalidateDaemon);
        });
    }

    public AspireRdResource? FindResource(string declarationResourceName)
    {
        var byDisplayName = _model.Resources.Values.FirstOrDefault(resource =>
            string.Equals(resource.DisplayName, declarationResourceName, StringComparison.Ordinal));
        return byDisplayName ?? _model.Resources.Values.FirstOrDefault(resource =>
            string.Equals(resource.Name, declarationResourceName, StringComparison.Ordinal));
    }

    public void ExecuteResourceCommand(string resourceName, string commandName)
    {
        _model.ExecuteResourceCommand(new ExecuteResourceCommandRequest(resourceName, commandName));
    }

    // ReSharper disable once CognitiveComplexity
    private void InvalidateDaemon()
    {
        using (_solution.Locks.UsingReadLock())
        {
            var documentHost = _solution.GetDocumentHost();
            var invalidatedDocuments = new HashSet<IDocument>();

            foreach (var project in _solution.GetAllProjects())
            {
                if (!project.IsAspireHostProject()) continue;

                foreach (var projectFile in project.GetAllProjectFiles(AspireResourceDeclarationCollector.IsApplicable))
                {
                    foreach (var document in documentHost.GetAllDocuments(projectFile.Location))
                    {
                        if (!invalidatedDocuments.Add(document)) continue;
                        _daemon.Invalidate("AspireResourceProtocolHost.ResourcesChanged".NON_LOCALIZABLE(), document);
                    }
                }
            }
        }
    }
}
