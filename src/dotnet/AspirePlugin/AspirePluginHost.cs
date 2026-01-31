using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.IDE;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Rider.Aspire.Plugin.ProjectModel;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin;

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
        model.GetReferencedProjectsFromAppHost.SetSync((lt, req) => GetReferencedProjectsFromAppHost(req, lt));
    }

    private string? GetProjectOutputType(RdPath projectPath)
    {
        IProject? project;
        using (_solution.Locks.UsingReadLock())
        {
            project = _solution.FindProjectByProjectFilePath(projectPath.FromRd());
        }

        return project?.GetRequestedProjectProperties(OutputTypeProjectPropertyRequest.OutputType).FirstNotNull();
    }

    private ReferenceProjectsFromAppHostResponse? ReferenceProjectsFromAppHost(
        ReferenceProjectsFromAppHostRequest request,
        Lifetime lifetime)
    {
        return _projectModelService.ReferenceProjectsFromAppHost(
            request.HostProjectFilePath.FromRd(),
            request.ProjectFilePaths.Select(it => it.FromRd()).ToList(),
            lifetime);
    }

    private ReferenceServiceDefaultsFromProjectsResponse? ReferenceServiceDefaultsFromProjects(
        ReferenceServiceDefaultsFromProjectsRequest request,
        Lifetime lifetime)
    {
        return _projectModelService.ReferenceServiceDefaultsFromProjects(
            request.SharedProjectFilePath.FromRd(),
            request.ProjectFilePaths.Select(it => it.FromRd()).ToList(),
            lifetime);
    }

    private GetReferencedProjectsFromAppHostResponse? GetReferencedProjectsFromAppHost(
        GetReferencedProjectsFromAppHostRequest request,
        Lifetime lifetime)
    {
        return _projectModelService.GetReferencedProjectsFromAppHost(
            request.HostProjectFilePath.FromRd(),
            request.ProjectFilePaths.Select(it => it.FromRd()).ToList(),
            lifetime);
    }
}