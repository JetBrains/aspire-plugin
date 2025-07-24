using JetBrains.Application.Components;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.ProjectsHost.SolutionHost;
using JetBrains.RdBackend.Common.Features.ProjectModel;
using JetBrains.Rider.Aspire.Generated;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Project;

/// <summary>
/// A service providing project model manipulation capabilities, primarily for handling project
/// references in the context of the Aspire framework.
/// </summary>
/// <remarks>
/// This service is responsible for managing and manipulating project references by interacting with the
/// solution's project model. It enables operations such as referencing multiple projects from a host project
/// or referencing shared defaults from other projects.
/// </remarks>
[SolutionComponent(InstantiationEx.LegacyDefault)]
public class AspireProjectModelService(ISolution solution, ILogger logger)
{
    /// <summary>
    /// References multiple projects from an Aspire Host project.
    /// </summary>
    /// <param name="hostProjectFilePath">The file path of the Aspire Host project which will reference other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects to be referenced in the host project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public ReferenceProjectsFromAppHostResponse? ReferenceProjectsFromAppHost(string hostProjectFilePath,
        List<string> projectFilePaths,
        Lifetime lifetime)
    {
        var hostProjectPath = hostProjectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (hostProjectPath is null)
        {
            logger.Warn($"Unable to parse a host project file path: {hostProjectFilePath}");
            return null;
        }

        var projectPaths = projectFilePaths
            .Select(it => it.ParseVirtualPath(InteractionContext.SolutionContext))
            .WhereNotNull()
            .ToList();
        if (projectPaths.Count == 0)
        {
            logger.Warn("No project file paths to reference from AppHost found");
            return null;
        }

        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var hostProject = solution.FindProjectByPath(hostProjectPath);
        if (hostProject is null)
        {
            logger.Warn("Unable to find a Host project");
            return null;
        }

        var referencedProjectPaths = new List<string>(projectFilePaths.Count);
        foreach (var projectPath in projectPaths)
        {
            var project = AddProjectReference(hostProject, projectPath);
            if (project is null)
            {
                continue;
            }

            referencedProjectPaths.Add(projectPath.FullPath);
            lifetime.ThrowIfNotAlive();
        }

        return new ReferenceProjectsFromAppHostResponse(referencedProjectPaths);
    }

    /// <summary>
    /// References an Aspire Shared project from specified projects.
    /// </summary>
    /// <param name="sharedProjectFilePath">The file path of the Aspire Shared project to be referenced in other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects which will reference the shared project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public ReferenceServiceDefaultsFromProjectsResponse? ReferenceServiceDefaultsFromProjects(
        string sharedProjectFilePath, List<string> projectFilePaths,
        Lifetime lifetime)
    {
        var sharedProjectPath = sharedProjectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (sharedProjectPath is null)
        {
            logger.Warn($"Unable to parse a shared project file path: {sharedProjectFilePath}");
            return null;
        }

        var projectPaths = projectFilePaths
            .Select(it => it.ParseVirtualPath(InteractionContext.SolutionContext))
            .WhereNotNull()
            .ToList();
        if (projectPaths.Count == 0)
        {
            logger.Warn("No project file paths to reference ServiceDeaults found");
            return null;
        }

        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var sharedProject = solution.FindProjectByPath(sharedProjectPath);
        if (sharedProject is null)
        {
            logger.Warn("Unable to find a Shared project");
            return null;
        }

        var projectPathsWithReference = new List<string>(projectFilePaths.Count);
        foreach (var projectPath in projectPaths)
        {
            var project = AddProjectReference(projectPath, sharedProject);
            if (project is null)
            {
                continue;
            }

            projectPathsWithReference.Add(projectPath.FullPath);
            lifetime.ThrowIfNotAlive();
        }

        return new ReferenceServiceDefaultsFromProjectsResponse(projectPathsWithReference);
    }

    private IProject? AddProjectReference(IProject fromProject, VirtualFileSystemPath toProjectPath)
    {
        using (solution.Locks.UsingReadLock())
        {
            var toProject = solution.FindProjectByProjectFilePath(toProjectPath);
            if (toProject is null)
            {
                logger.Warn("Unable to resolve project from a file path");
                return null;
            }

            AddProjectReference(fromProject, toProject);

            return toProject;
        }
    }

    private IProject? AddProjectReference(VirtualFileSystemPath fromProjectPath, IProject toProject)
    {
        using (solution.Locks.UsingReadLock())
        {
            var fromProject = solution.FindProjectByProjectFilePath(fromProjectPath);
            if (fromProject is null)
            {
                logger.Warn("Unable to resolve project from a file path");
                return null;
            }

            AddProjectReference(fromProject, toProject);

            return fromProject;
        }
    }

    private void AddProjectReference(IProject fromProject, IProject toProject)
    {
        solution.InvokeUnderTransaction(cookie =>
        {
            foreach (var tfm in fromProject.TargetFrameworkIds)
            {
                cookie.AddModuleReference(fromProject, toProject, tfm);
            }
        });
    }
}