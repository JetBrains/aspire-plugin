using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Core;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.Rider.Aspire.Files;
using JetBrains.Rider.Aspire.Generated;
using JetBrains.Rider.Aspire.Project;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire;

[SolutionComponent(InstantiationEx.LegacyDefault)]
public class AspirePluginHost
{
    private readonly ISolution _solution;
    private readonly AspireProjectModelService _projectModelService;
    private readonly AspireDefaultFileService _aspireDefaultFileService;

    public AspirePluginHost(ISolution solution, AspireProjectModelService projectModelService,
        AspireDefaultFileService aspireDefaultFileService)
    {
        _solution = solution;
        _projectModelService = projectModelService;
        _aspireDefaultFileService = aspireDefaultFileService;

        var model = solution.GetProtocolSolution().GetAspirePluginModel();
        model.GetProjectOutputType.SetSync(GetProjectOutputType);
        model.ReferenceProjectsFromAppHost.SetSync((lt, req) => ReferenceProjectsFromAppHost(req, lt));
        model.ReferenceServiceDefaultsFromProjects.SetSync((lt, req) => ReferenceServiceDefaultsFromProjects(req, lt));
        model.InsertProjectsIntoAppHostFile.SetSync((lt, req) => InsertProjectsIntoAppHostFile(req, lt));
        model.InsertDefaultMethodsIntoProjectProgramFile.SetSync((lt, req) =>
            InsertDefaultMethodsIntoProjectProgramFile(req, lt));
    }

    private string? GetProjectOutputType(string projectPath)
    {
        var projectFilePath = projectPath.ParseVirtualPathSafe(InteractionContext.SolutionContext);
        IProject? project;
        using (_solution.Locks.UsingReadLock())
        {
            project = _solution.FindProjectByProjectFilePath(projectFilePath);
        }

        return project?.GetRequestedProjectProperties(OutputTypeProjectPropertyRequest.OutputType).FirstNotNull();
    }

    private ReferenceProjectsFromAppHostResponse? ReferenceProjectsFromAppHost(
        ReferenceProjectsFromAppHostRequest request,
        Lifetime lifetime)
    {
        return _projectModelService.ReferenceProjectsFromAppHost(request.HostProjectFilePath,
            request.ProjectFilePaths, lifetime);
    }

    private ReferenceServiceDefaultsFromProjectsResponse? ReferenceServiceDefaultsFromProjects(
        ReferenceServiceDefaultsFromProjectsRequest request,
        Lifetime lifetime)
    {
        return _projectModelService.ReferenceServiceDefaultsFromProjects(request.SharedProjectFilePath,
            request.ProjectFilePaths, lifetime);
    }

    private Unit InsertProjectsIntoAppHostFile(InsertProjectsIntoAppHostFileRequest request,
        Lifetime lifetime)
    {
        _aspireDefaultFileService.InsertProjectsIntoAppHostFile(request.HostProjectFilePath, request.ProjectFilePaths,
            lifetime);
        return Unit.Instance;
    }

    private Unit InsertDefaultMethodsIntoProjectProgramFile(InsertDefaultMethodsIntoProjectProgramFileRequest request,
        Lifetime lifetime)
    {
        _aspireDefaultFileService.InsertDefaultMethodsIntoProjectProgramFile(request.ProjectFilePath, lifetime);
        return Unit.Instance;
    }
}