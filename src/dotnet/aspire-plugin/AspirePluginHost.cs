using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.Rider.Aspire.Generated;
using JetBrains.Rider.Aspire.Project;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class AspirePluginHost
{
    private readonly ISolution _solution;

    public AspirePluginHost(ISolution solution)
    {
        _solution = solution;
        var model = solution.GetProtocolSolution().GetAspirePluginModel();

        model.GetProjectOutputType.SetSync(GetProjectOutputType);
    }

    private string? GetProjectOutputType(string projectPath)
    {
        var projectFilePath = projectPath.ParseVirtualPathSafe(InteractionContext.Local);
        IProject? project;
        using (_solution.Locks.UsingReadLock())
        {
            project = _solution.FindProjectByProjectFilePath(projectFilePath);
        }

        return project?.GetRequestedProjectProperties(OutputTypeProjectPropertyRequest.OutputType).FirstNotNull();
    }
}