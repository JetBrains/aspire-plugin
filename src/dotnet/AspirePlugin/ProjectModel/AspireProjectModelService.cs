using JetBrains.Application.Components;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.IDE;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Impl;
using JetBrains.ProjectModel.ProjectsHost.SolutionHost;
using JetBrains.RdBackend.Common.Features.ProjectModel;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Util;
using JetBrains.Util.Dotnet.TargetFrameworkIds;

namespace JetBrains.Rider.Aspire.Plugin.ProjectModel;

/// <summary>
/// A service providing project model manipulation capabilities, primarily for handling project
/// references in the context of the Aspire framework.
/// </summary>
/// <remarks>
/// This service is responsible for managing and manipulating project references by interacting with the
/// solution's project model. It enables operations such as referencing multiple projects from a host project
/// or referencing shared defaults from other projects.
/// </remarks>
[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireProjectModelService(ISolution solution, ILogger logger)
{
    /// <summary>
    /// References multiple projects from an Aspire Host project.
    /// </summary>
    /// <param name="hostProjectFilePath">The file path of the Aspire Host project which will reference other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects to be referenced in the host project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public ReferenceProjectsFromAppHostResponse? ReferenceProjectsFromAppHost(
        VirtualFileSystemPath hostProjectFilePath,
        List<VirtualFileSystemPath> projectFilePaths,
        Lifetime lifetime)
    {
        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var hostProject = solution.FindProjectByPath(hostProjectFilePath);
        if (hostProject is null)
        {
            logger.Warn("Unable to find a Host project");
            return null;
        }

        var referencedProjectPaths = new List<VirtualFileSystemPath>(projectFilePaths.Count);
        foreach (var projectPath in projectFilePaths)
        {
            var project = AddProjectReference(hostProject, projectPath);
            if (project is null)
            {
                continue;
            }

            referencedProjectPaths.Add(projectPath);
            lifetime.ThrowIfNotAlive();
        }

        var resultPaths = referencedProjectPaths.Select(it => it.ToRd()).ToList();
        return new ReferenceProjectsFromAppHostResponse(resultPaths);
    }

    /// <summary>
    /// References an Aspire Shared project from specified projects.
    /// </summary>
    /// <param name="sharedProjectFilePath">The file path of the Aspire Shared project to be referenced in other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects which will reference the shared project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public ReferenceServiceDefaultsFromProjectsResponse? ReferenceServiceDefaultsFromProjects(
        VirtualFileSystemPath sharedProjectFilePath,
        List<VirtualFileSystemPath> projectFilePaths,
        Lifetime lifetime)
    {
        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var sharedProject = solution.FindProjectByPath(sharedProjectFilePath);
        if (sharedProject is null)
        {
            logger.Warn("Unable to find a Shared project");
            return null;
        }

        var projectPathsWithReference = new List<VirtualFileSystemPath>(projectFilePaths.Count);
        foreach (var projectPath in projectFilePaths)
        {
            var project = AddProjectReference(projectPath, sharedProject);
            if (project is null)
            {
                continue;
            }

            projectPathsWithReference.Add(projectPath);
            lifetime.ThrowIfNotAlive();
        }

        var resultPaths = projectPathsWithReference.Select(it => it.ToRd()).ToList();
        return new ReferenceServiceDefaultsFromProjectsResponse(resultPaths);
    }

    private IProject? AddProjectReference(IProject targetProject, VirtualFileSystemPath projectToReference)
    {
        using (solution.Locks.UsingReadLock())
        {
            var toProject = solution.FindProjectByProjectFilePath(projectToReference);
            if (toProject is null)
            {
                logger.Warn("Unable to resolve project from a file path");
                return null;
            }

            AddProjectReference(targetProject, toProject);

            return toProject;
        }
    }

    private IProject? AddProjectReference(VirtualFileSystemPath targetProject, IProject projectToReference)
    {
        using (solution.Locks.UsingReadLock())
        {
            var fromProject = solution.FindProjectByProjectFilePath(targetProject);
            if (fromProject is null)
            {
                logger.Warn("Unable to resolve project from a file path");
                return null;
            }

            AddProjectReference(fromProject, projectToReference);

            return fromProject;
        }
    }

    private void AddProjectReference(IProject targetProject, IProject projectToReference)
    {
        var existingReferences = new Dictionary<TargetFrameworkId, List<Guid>>(targetProject.TargetFrameworkIds.Count);
        foreach (var tfm in targetProject.TargetFrameworkIds)
        {
            if (tfm is null) continue;
            var references = targetProject.GetProjectReferences(tfm);
            existingReferences[tfm] = references
                .SelectNotNull(it =>
                {
                    if (it is GuidProjectReference gpr)
                        return (Guid?)gpr.ReferencedProjectGuid;
                    else
                        return null;
                }).ToList();
        }

        solution.InvokeUnderTransaction(cookie =>
        {
            foreach (var tfm in targetProject.TargetFrameworkIds)
            {
                if (existingReferences[tfm].Contains(projectToReference.Guid))
                {
                    continue;
                }

                cookie.AddModuleReference(targetProject, projectToReference, tfm);
            }
        });
    }
}