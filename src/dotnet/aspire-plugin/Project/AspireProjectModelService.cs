using System.Diagnostics;
using System.Text;
using JetBrains.Application.Components;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.DocumentManagers;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.ProjectsHost.SolutionHost;
using JetBrains.RdBackend.Common.Features.ProjectModel;
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
    private const string AddServiceDefaults = "builder.AddServiceDefaults();";
    private const string MapDefaultEndpoints = "app.MapDefaultEndpoints();";
    private const string AddProjectMethod = ".AddProject(";
    private const string CreateBuilderMethod = ".CreateBuilder(";
    private const string BuildMethod = ".Build(";

    /// <summary>
    /// References multiple projects from an Aspire Host project.
    /// </summary>
    /// <param name="hostProjectFilePath">The file path of the Aspire Host project which will reference other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects to be referenced in the host project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public void ReferenceProjectsFromAppHost(string hostProjectFilePath, List<string> projectFilePaths,
        Lifetime lifetime)
    {
        var hostProjectPath = hostProjectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (hostProjectPath is null)
        {
            logger.Warn($"Unable to parse a host project file path: {hostProjectFilePath}");
            return;
        }

        var projectPaths = projectFilePaths
            .Select(it => it.ParseVirtualPath(InteractionContext.SolutionContext))
            .WhereNotNull()
            .ToList();
        if (projectPaths.Count == 0)
        {
            logger.Warn("No project file paths to reference from AppHost found");
            return;
        }

        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var hostProject = FindProjectByPath(hostProjectPath);
        if (hostProject is null)
        {
            logger.Warn("Unable to find a Host project");
            return;
        }

        foreach (var projectPath in projectPaths)
        {
            AddProjectReference(hostProject, projectPath);
            InsertProjectIntoAppHostFile(hostProject, projectPath);
            lifetime.ThrowIfNotAlive();
        }
    }

    /// <summary>
    /// References an Aspire Shared project from specified projects.
    /// </summary>
    /// <param name="sharedProjectFilePath">The file path of the Aspire Shared project to be referenced in other projects.</param>
    /// <param name="projectFilePaths">A list of file paths for the projects which will reference the shared project.</param>
    /// <param name="lifetime">The <see cref="Lifetime"/> object controlling the scope of the operation, allowing cancellation if needed.</param>
    public void ReferenceServiceDefaultsFromProjects(string sharedProjectFilePath, List<string> projectFilePaths,
        Lifetime lifetime)
    {
        var sharedProjectPath = sharedProjectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (sharedProjectPath is null)
        {
            logger.Warn($"Unable to parse a shared project file path: {sharedProjectFilePath}");
            return;
        }

        var projectPaths = projectFilePaths
            .Select(it => it.ParseVirtualPath(InteractionContext.SolutionContext))
            .WhereNotNull()
            .ToList();
        if (projectPaths.Count == 0)
        {
            logger.Warn("No project file paths to reference ServiceDeaults found");
            return;
        }

        var solutionHost = solution.ProjectsHostContainer().GetComponent<ISolutionHost>();
        solutionHost.SuspendAsyncProjectsReloading(lifetime);

        var sharedProject = FindProjectByPath(sharedProjectPath);
        if (sharedProject is null)
        {
            logger.Warn("Unable to find a Shared project");
            return;
        }

        foreach (var projectPath in projectPaths)
        {
            var project = AddProjectReference(projectPath, sharedProject);
            if (project is null)
            {
                lifetime.ThrowIfNotAlive();
                continue;
            }

            InsertDefaultMethodIntoProjectProgramFile(project, AddServiceDefaults, CreateBuilderMethod);
            InsertDefaultMethodIntoProjectProgramFile(project, MapDefaultEndpoints, BuildMethod);

            lifetime.ThrowIfNotAlive();
        }
    }

    private IProject? FindProjectByPath(VirtualFileSystemPath path)
    {
        IProject? project;
        using (solution.Locks.UsingReadLock())
        {
            project = solution.FindProjectByProjectFilePath(path);
        }

        return project;
    }

    private void AddProjectReference(IProject fromProject, VirtualFileSystemPath toProjectPath)
    {
        using (solution.Locks.UsingReadLock())
        {
            var toProject = solution.FindProjectByProjectFilePath(toProjectPath);
            if (toProject is null)
            {
                logger.Warn("Unable to resolve project from a file path");
                return;
            }

            AddProjectReference(fromProject, toProject);
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

    private void InsertProjectIntoAppHostFile(IProject hostProject, VirtualFileSystemPath projectToInsertPath)
    {
        // Debugger.Launch();
        var projectName = projectToInsertPath.NameWithoutExtension;

        using (solution.Locks.UsingWriteLock())
        {
            var appHostFile = hostProject.GetSubFiles("AppHost.cs").SingleItem();
            if (appHostFile is null)
            {
                logger.Warn("Unable to find AppHost.cs file in the AppHost project");
                return;
            }

            var document = appHostFile.GetDocument();

            var currentText = document.GetText();

            var methodToInsert = CreateAddProjectMethodToInsert(projectName);
            if (currentText.Contains(methodToInsert))
            {
                return;
            }

            var lastAddProjectIndex = currentText.LastIndexOf(AddProjectMethod, StringComparison.Ordinal);
            if (lastAddProjectIndex == -1)
            {
                logger.Trace("AppHost.cs doesn't contain other projects");

                var builderIndex = currentText.IndexOf(CreateBuilderMethod, StringComparison.Ordinal);
                if (builderIndex == -1)
                {
                    logger.Warn("Unable to find creating a distributed builder");
                    return;
                }

                var semicolonIndex = currentText.IndexOf(';', builderIndex);
                if (semicolonIndex == -1)
                {
                    logger.Warn("Unable to find a semicolon after the distributed builder creation");
                    return;
                }

                InsertAddProjectAfterSemicolon(semicolonIndex, methodToInsert, document);
            }
            else
            {
                logger.Trace("AppHost.cs contains other projects");

                var semicolonIndex = currentText.IndexOf(';', lastAddProjectIndex);
                if (semicolonIndex == -1)
                {
                    logger.Warn("Unable to find a semicolon after the project adding");
                    return;
                }

                InsertAddProjectAfterSemicolon(semicolonIndex, methodToInsert, document);
            }
        }
    }

    private static string CreateAddProjectMethodToInsert(string projectName)
    {
        var sb = new StringBuilder();
        sb.Append("builder.AddProject<Projects.");
        sb.Append(projectName);
        sb.Append(">(\"");
        sb.Append(projectName.ToLower());
        sb.Append("\");");
        return sb.ToString();
    }

    private static void InsertAddProjectAfterSemicolon(int semicolonIndex, string methodToInsert, IDocument document)
    {
        var offset = new DocumentOffset(document, semicolonIndex + 1);
        var line = offset.ToDocumentCoords().Line;
        var indent = DocumentIndentUtils.GetLineIndent(document, line);
        var sb = new StringBuilder();
        sb.Append('\n');
        sb.Append('\n');
        sb.Append(indent);
        sb.Append(methodToInsert);
        document.InsertText(offset, sb.ToString());
    }

    private void InsertDefaultMethodIntoProjectProgramFile(IProject project, string methodToInsert,
        string insertAfterMethod)
    {
        using (solution.Locks.UsingWriteLock())
        {
            var programFile = project.GetSubFiles("Program.cs").SingleItem();
            if (programFile is null)
            {
                logger.Info("Unable to find Program.cs file in the project");
                return;
            }

            var document = programFile.GetDocument();

            var currentText = document.GetText();

            if (currentText.Contains(methodToInsert))
            {
                return;
            }

            InsertMethodCallIntoProjectProgramFile(methodToInsert, insertAfterMethod, document, currentText);
        }
    }

    private void InsertMethodCallIntoProjectProgramFile(string methodToInsert, string insertAfterMethod,
        IDocument document, string currentText)
    {
        var methodIndex = currentText.IndexOf(insertAfterMethod, StringComparison.Ordinal);
        if (methodIndex == -1)
        {
            logger.Info($"Unable to find {insertAfterMethod} method in the Program.cs file");
            return;
        }

        var semicolonIndex = currentText.IndexOf(';', methodIndex);
        if (semicolonIndex == -1)
        {
            logger.Info($"Unable to find a semicolon after the {insertAfterMethod} method");
            return;
        }

        var offset = new DocumentOffset(document, semicolonIndex + 1);
        var line = offset.ToDocumentCoords().Line;
        var indent = DocumentIndentUtils.GetLineIndent(document, line);
        var sb = new StringBuilder();
        sb.Append('\n');
        sb.Append('\n');
        sb.Append(indent);
        sb.Append(methodToInsert);
        document.InsertText(offset, sb.ToString());
    }
}