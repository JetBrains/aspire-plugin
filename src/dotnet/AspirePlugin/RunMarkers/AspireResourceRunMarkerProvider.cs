using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.Application.Settings;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Aspire.Plugin.AspireResources;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Rider.Backend.Features.RunMarkers;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[Language(typeof(CSharpLanguage))]
[HighlightingSource(HighlightingTypes = [typeof(IRunMarkerHighlighting)])]
[ZoneMarker(typeof(ILanguageCSharpZone))]
public class AspireResourceRunMarkerProvider : IRunMarkerProvider
{
    public void CollectRunMarkers(IFile file, IContextBoundSettingsStore settings, IHighlightingConsumer consumer)
    {
        if (file is not ICSharpFile csharpFile) return;
        if (!AspireResourceDeclarationCollector.IsApplicable(file)) return;

        var project = file.GetProject();
        if (project == null) return;
        var resourceProtocolHost = project.GetSolution().GetComponent<AspireResourceProtocolHost>();
        foreach (var declaration in AspireResourceDeclarationCollector.Collect(csharpFile))
        {
            var resource = resourceProtocolHost.FindResource(declaration.ResourceName);
            if (resource == null) continue;
            if (!resource.Commands.Any(command => command.State == AspireRdResourceCommandState.Enabled)) continue;

            var highlighting = new AspireResourceRunMarkerHighlighting(
                project,
                resource.Name,
                declaration.ResourceName,
                GetToolTip(resource),
                AspireResourceRunMarkerAttributeIds.AspireResourceMarkerId,
                declaration.Range,
                file.GetPsiModule().TargetFrameworkId);
            consumer.AddHighlighting(highlighting, declaration.Range);
        }
    }

    public double Priority => RunMarkerProviderPriority.DEFAULT;

    private static string GetToolTip(AspireRdResource resource)
    {
        var resourceName = string.IsNullOrEmpty(resource.DisplayName) ? resource.Name : resource.DisplayName;
        return $"Aspire resource '{resourceName}'";
    }
}
