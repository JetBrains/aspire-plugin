using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.IDE;
using JetBrains.ProjectModel;
using JetBrains.RdBackend.Common.Features.ProjectModel;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.Rider.Aspire.Plugin.AspireResources;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.ProjectModel;

/// <summary>
/// Resolves the entry point of an Aspire AppHost: the file and the line of its
/// <c>DistributedApplication.CreateBuilder(...)</c> call.
/// </summary>
/// <remarks>
/// The Aspire CLI runner debugs the AppHost by making it block inside <c>CreateBuilder</c> until a debugger
/// attaches, so breakpoints at or above that line can never be hit. The frontend uses the location reported
/// here to mark those breakpoints as unreachable.
/// </remarks>
[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireAppHostEntryPointService(ISolution solution, ILogger logger)
{
    private const string CSharpExtension = "cs";

    /// <summary>
    /// Finds the AppHost entry point for <paramref name="appHostFilePath"/>, which is either a project file
    /// (<c>MyApp.AppHost.csproj</c>) or a file-based AppHost (<c>apphost.cs</c>).
    /// </summary>
    /// <returns>The entry point, or <c>null</c> when no <c>CreateBuilder</c> call can be found.</returns>
    public GetAppHostEntryPointResponse? GetAppHostEntryPoint(VirtualFileSystemPath appHostFilePath)
    {
        using (solution.Locks.UsingReadLock())
        {
            foreach (var projectFile in GetCandidateProjectFiles(appHostFilePath))
            {
                if (projectFile.GetPrimaryPsiFile() is not ICSharpFile csharpFile) continue;

                var line = AspireAppHostEntryPointCollector.TryFindCreateBuilderLine(csharpFile);
                if (line is null) continue;

                return new GetAppHostEntryPointResponse(projectFile.Location.ToRd(), line.Value);
            }
        }

        logger.Verbose($"Unable to find a DistributedApplication.CreateBuilder call for {appHostFilePath}");
        return null;
    }

    /// <summary>
    /// Returns the C# files that could hold the AppHost's <c>CreateBuilder</c> call, most likely first.
    /// </summary>
    private IEnumerable<IProjectFile> GetCandidateProjectFiles(VirtualFileSystemPath appHostFilePath)
    {
        // A file-based AppHost (`aspire run --apphost apphost.cs`) points straight at its entry file.
        if (string.Equals(appHostFilePath.ExtensionNoDot, CSharpExtension, StringComparison.OrdinalIgnoreCase))
        {
            return solution.FindProjectItemsByLocation(appHostFilePath).OfType<IProjectFile>();
        }

        var project = solution.FindProjectByProjectFilePath(appHostFilePath);
        if (project is null)
        {
            logger.Verbose($"Unable to resolve an AppHost project from {appHostFilePath}");
            return [];
        }

        var csharpProjectFiles = project
            .GetAllProjectFiles(static projectFile => projectFile.LanguageType is CSharpProjectFileType)
            .ToList();

        // Try the conventional entry-file names first, then every other C# file in the project so that a
        // renamed entry file still resolves.
        var isConventionalName = (IProjectFile projectFile) =>
            AspireResourceDeclarationCollector.OurAppHostFileNames.Contains(projectFile.Name);

        return csharpProjectFiles.Where(isConventionalName)
            .Concat(csharpProjectFiles.Where(it => !isConventionalName(it)));
    }
}
