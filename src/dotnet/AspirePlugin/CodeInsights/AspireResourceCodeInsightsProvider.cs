using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.Rider.Model;

namespace JetBrains.Rider.Aspire.Plugin.CodeInsights;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireResourceCodeInsightsProvider : ICodeInsightsProvider
{
    public const string Id = "Aspire Resource State";

    public string ProviderId => Id;

    public string DisplayName => "Aspire resource state";

    public CodeVisionAnchorKind DefaultAnchor => CodeVisionAnchorKind.Top;

    public ICollection<CodeVisionRelativeOrdering> RelativeOrderings { get; } =
        new CodeVisionRelativeOrdering[] { new CodeVisionRelativeOrderingLast() };

    public bool IsAvailableIn(ISolution solution) => solution.GetAllProjects().Any(project => project.IsAspireHostProject());

    public void OnClick(CodeInsightHighlightInfo highlightInfo, ISolution solution, CodeInsightsClickInfo clickInfo)
    {
    }

    public void OnExtraActionClick(CodeInsightHighlightInfo highlightInfo, string actionId, ISolution solution)
    {
    }
}
