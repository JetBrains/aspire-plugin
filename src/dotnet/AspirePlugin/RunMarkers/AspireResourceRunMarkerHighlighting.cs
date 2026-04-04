using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.Rider.Backend.Features.RunMarkers;
using JetBrains.Util.Dotnet.TargetFrameworkIds;

namespace JetBrains.Rider.Aspire.Plugin.RunMarkers;

[StaticSeverityHighlighting(Severity.INFO, typeof(RunMarkerHighlighting.RunMarkers), OverlapResolve = OverlapResolveKind.NONE)]
public class AspireResourceRunMarkerHighlighting(
    IProject project,
    string fullName,
    string declarationResourceName,
    string toolTip,
    string attributeId,
    DocumentRange range,
    TargetFrameworkId targetFrameworkId) : IRunMarkerHighlighting
{
    public IProject Project { get; } = project;

    public string AttributeId { get; } = attributeId;

    public TargetFrameworkId TargetFrameworkId { get; } = targetFrameworkId;

    public string FullName { get; } = fullName;

    public string DeclarationResourceName { get; } = declarationResourceName;

    public string ToolTip { get; } = toolTip;

    public string? ErrorStripeToolTip => null;

    public bool IsValid() => range.IsValid();

    public DocumentRange CalculateRange() => range;
}
