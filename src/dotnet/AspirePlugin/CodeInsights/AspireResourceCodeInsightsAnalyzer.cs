using JetBrains.Application.Parts;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Aspire.Plugin.AspireResources;
using JetBrains.Rider.Aspire.Plugin.RunMarkers;

namespace JetBrains.Rider.Aspire.Plugin.CodeInsights;

[ElementProblemAnalyzer(
    Instantiation.DemandAnyThreadUnsafe,
    typeof(ICSharpFile),
    HighlightingTypes = new[] { typeof(CodeInsightsHighlighting) })]
public class AspireResourceCodeInsightsAnalyzer(
    AspireResourceCodeInsightsProvider provider,
    AspireResourceProtocolHost resourceProtocolHost)
    : ElementProblemAnalyzer<ICSharpFile>, IConditionalElementProblemAnalyzer
{
    public bool ShouldRun(IFile file, ElementProblemAnalyzerData data)
    {
        return AspireResourceDeclarationCollector.IsApplicable(file);
    }

    protected override void Run(ICSharpFile file, ElementProblemAnalyzerData data, IHighlightingConsumer consumer)
    {
        foreach (var declaration in AspireResourceDeclarationCollector.Collect(file))
        {
            var resource = resourceProtocolHost.FindResource(declaration.ResourceName);
            if (resource == null) continue;

            var description = AspireResourcePresentation.GetDescriptionText(resource);
            consumer.AddHighlighting(new CodeInsightsHighlighting(
                declaration.Range,
                AspireResourcePresentation.GetDisplayText(resource),
                description,
                description,
                provider,
                null,
                null));
        }
    }
}