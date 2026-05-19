using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.Rider.Model;

namespace JetBrains.Rider.Aspire.Plugin.CodeInsights;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireResourceCodeInsightsProvider : ICodeInsightsProvider
{
    private const string Id = "Aspire Resource State";

    public string ProviderId => Id;

    public string DisplayName => "Aspire resource state";

    public CodeVisionAnchorKind DefaultAnchor => CodeVisionAnchorKind.Top;

    public ICollection<CodeVisionRelativeOrdering> RelativeOrderings { get; } = [new CodeVisionRelativeOrderingFirst()];

    public bool IsAvailableIn(ISolution solution) => true;

    public void OnClick(CodeInsightHighlightInfo highlightInfo, ISolution solution, CodeInsightsClickInfo? clickInfo)
    {
    }

    public void OnExtraActionClick(CodeInsightHighlightInfo highlightInfo, string actionId, ISolution solution)
    {
    }
}