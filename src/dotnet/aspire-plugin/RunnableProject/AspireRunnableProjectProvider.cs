using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Features.Running;
using JetBrains.Rider.Model;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.RunnableProject;

[SolutionComponent(Instantiation.DemandAnyThreadUnsafe)]
public class AspireRunnableProjectProvider(
    ProjectRunnableOutputDetector projectRunnableOutputDetector,
    ILogger logger
) : IRunnableProjectProvider
{
    public Model.RunnableProject? CreateRunnableProject(
        IProject project,
        string name,
        string fullName,
        IconModel? icon)
    {
        if (!project.IsDotNetCoreProject()) return null;

        if (!project.IsAspireHostProject()) return null;

        var projectOutputs = new List<ProjectOutput>();
        foreach (var tfm in project.TargetFrameworkIds)
        {
            var projectOutput = projectRunnableOutputDetector.CalculateProjectOutput(project, tfm);
            if (projectOutput == null)
            {
                logger.Trace($"Unable to find output for project for {tfm}");
                continue;
            }

            projectOutputs.Add(projectOutput);
        }

        return new Model.RunnableProject(
            name,
            fullName,
            project.ProjectFileLocation.NormalizeSeparators(FileSystemPathEx.SeparatorStyle.Unix),
            AspireRunnableProjectKinds.AspireHost,
            projectOutputs,
            [],
            null,
            []
        );
    }

    public IEnumerable<RunnableProjectKind> HiddenRunnableProjectKinds => EmptyList<RunnableProjectKind>.Instance;
}