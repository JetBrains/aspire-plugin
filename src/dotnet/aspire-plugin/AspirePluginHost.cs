using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Core;
using JetBrains.Lifetimes;
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
    private readonly AspireProjectModelService _projectModelService;

    public AspirePluginHost(ISolution solution, AspireProjectModelService projectModelService)
    {
        _solution = solution;
        _projectModelService = projectModelService;

        var model = solution.GetProtocolSolution().GetAspirePluginModel();
        model.GetProjectOutputType.SetSync(GetProjectOutputType);
        model.ReferenceProjectsFromAppHost.SetSync((lt, req) => ReferenceProjectsFromAppHost(req, lt));
        model.ReferenceServiceDefaultsFromProjects.SetSync((lt, req) => ReferenceServiceDefaultsFromProjects(req, lt));
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

    private Unit ReferenceProjectsFromAppHost(ReferenceProjectsFromAppHostRequest request,
        Lifetime lifetime)
    {
        _projectModelService.ReferenceProjectsFromAppHost(request.HostProjectFilePath,
            request.ProjectFilePaths, lifetime);
        return Unit.Instance;
    }

    private Unit ReferenceServiceDefaultsFromProjects(ReferenceServiceDefaultsFromProjectsRequest request,
        Lifetime lifetime)
    {
        _projectModelService.ReferenceServiceDefaultsFromProjects(request.SharedProjectFilePath,
            request.ProjectFilePaths, lifetime);
        return Unit.Instance;
    }
}