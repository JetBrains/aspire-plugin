using System.Collections.Generic;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;
using JetBrains.ReSharper.Features.Running;
using JetBrains.Rider.Model;
using JetBrains.Util;
using static JetBrains.Rider.Aspire.Project.AspireHostProjectPropertyRequest;

namespace JetBrains.Rider.Aspire.RunnableProject;

[SolutionComponent]
public class AspireRunnableProjectProvider(
    ProjectRunnableOutputDetector projectRunnableOutputDetector,
    ILogger logger
) : IRunnableProjectProvider
{
    public JetBrains.Rider.Model.RunnableProject? CreateRunnableProject(
        IProject project,
        string name,
        string fullName,
        IconModel? icon)
    {
        if (!project.IsDotNetCoreProject()) return null;

        var isAspireHost = project.GetUniqueRequestedProjectProperty(IsAspireHost);
        if (isAspireHost.IsNullOrEmpty() || isAspireHost != "true") return null;

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

        return new JetBrains.Rider.Model.RunnableProject(
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