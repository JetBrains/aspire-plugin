using System.Text;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.DocumentManagers;
using JetBrains.DocumentModel;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.Rider.Aspire.Plugin.Project;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.Files;

/// A service that provides functionality for inserting default configurations
/// and method calls into project files, such as `AppHost.cs` and `Program.cs`.
[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireDefaultFileService(ISolution solution, ILogger logger)
{
    private const string AddServiceDefaults = "builder.AddServiceDefaults();";
    private const string MapDefaultEndpoints = "app.MapDefaultEndpoints();";
    private const string AddProjectMethod = ".AddProject(";
    private const string CreateBuilderMethod = ".CreateBuilder(";
    private const string BuildMethod = ".Build(";

    /// Inserts a list of project references into an `AppHost.cs` file associated with a specified host project.
    /// <param name="hostProjectFilePath">
    /// The file path of the host project where the project references will be inserted.
    /// </param>
    /// <param name="projectFilePaths">
    /// A list of file paths for the projects to be inserted into the host project's AppHost file.
    /// </param>
    /// <param name="lifetime">
    /// The lifetime controlling the operation.
    /// </param>
    public void InsertProjectsIntoAppHostFile(string hostProjectFilePath, List<string> projectFilePaths,
        Lifetime lifetime)
    {
        var hostProjectPath = hostProjectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (hostProjectPath is null)
        {
            logger.Warn($"Unable to parse a host project file path: {hostProjectFilePath}");
            return;
        }

        var hostProject = solution.FindProjectByPath(hostProjectPath);
        if (hostProject is null)
        {
            logger.Warn("Unable to find a Host project");
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

        foreach (var projectPath in projectPaths)
        {
            InsertProjectIntoAppHostFile(hostProject, projectPath);
            lifetime.ThrowIfNotAlive();
        }
    }

    /// Injects default method calls into a project's `Program.cs` file.
    /// Specifically, inserts `AddServiceDefaults` and `MapDefaultEndpoints` methods.
    /// <param name="projectFilePath">
    /// The file path of the project file where the default methods will be inserted.
    /// </param>
    /// <param name="lifetime">
    /// The lifetime controlling this operation.
    /// </param>
    public void InsertDefaultMethodsIntoProjectProgramFile(string projectFilePath, Lifetime lifetime)
    {
        var projectPath = projectFilePath.ParseVirtualPath(InteractionContext.SolutionContext);
        if (projectPath is null)
        {
            logger.Warn($"Unable to parse a host project file path: {projectPath}");
            return;
        }

        var project = solution.FindProjectByPath(projectPath);
        if (project is null)
        {
            logger.Warn("Unable to find a Host project");
            return;
        }

        lifetime.ThrowIfNotAlive();
        InsertDefaultMethodIntoProjectProgramFile(project, AddServiceDefaults, CreateBuilderMethod);

        lifetime.ThrowIfNotAlive();
        InsertDefaultMethodIntoProjectProgramFile(project, MapDefaultEndpoints, BuildMethod);
    }

    private void InsertProjectIntoAppHostFile(IProject hostProject, VirtualFileSystemPath projectToInsertPath)
    {
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