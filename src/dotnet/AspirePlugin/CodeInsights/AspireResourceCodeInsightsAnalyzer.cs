using JetBrains.Application.Parts;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Aspire.Plugin.AspireResources;
using JetBrains.Rider.Backend.Platform.Icons;

namespace JetBrains.Rider.Aspire.Plugin.CodeInsights;

[ElementProblemAnalyzer(
    Instantiation.DemandAnyThreadUnsafe,
    typeof(ICSharpFile),
    HighlightingTypes = [typeof(CodeInsightsHighlighting)])]
public class AspireResourceCodeInsightsAnalyzer(
    AspireResourceCodeInsightsProvider provider,
    AspireResourceProtocolHost resourceProtocolHost,
    IconHost iconHost
) : ElementProblemAnalyzer<ICSharpFile>, IConditionalElementProblemAnalyzer
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

            var displayText = AspireResourcePresentation.GetDisplayText(resource);
            var description = AspireResourcePresentation.GetDescriptionText(resource);
            var iconId = AspireResourcePresentation.GetIconId(resource);

            consumer.AddHighlighting(new CodeInsightsHighlighting(
                declaration.Range,
                displayText,
                description,
                description,
                provider,
                null,
                iconHost.Transform(iconId)));
        }
    }
}