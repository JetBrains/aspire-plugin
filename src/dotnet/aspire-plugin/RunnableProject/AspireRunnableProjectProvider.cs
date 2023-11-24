using System.Collections.Generic;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;
using JetBrains.ReSharper.Features.Running;
using JetBrains.Rider.Model;
using JetBrains.Util;
using static AspirePlugin.RunnableProject.AspireProjectPropertyRequest;

namespace AspirePlugin.RunnableProject;

[SolutionComponent]
public class AspireRunnableProjectProvider : IRunnableProjectProvider
{
    public JetBrains.Rider.Model.RunnableProject? CreateRunnableProject(IProject project, string name, string fullName, IconModel? icon)
    {
        System.Diagnostics.Debugger.Launch();
        if (!project.IsDotNetCoreProject()) return null;

        var isAspireHost = project.GetUniqueRequestedProjectProperty(IsAspireHost);
        if (isAspireHost.IsNullOrEmpty() || isAspireHost != "true") return null;

        return new JetBrains.Rider.Model.RunnableProject(
            name,
            fullName,
            project.ProjectFileLocation.NormalizeSeparators(FileSystemPathEx.SeparatorStyle.Unix),
            AspireRunnableProjectKinds.AspireHost,
            [],
            [],
            null,
            []
        );
    }

    public IEnumerable<RunnableProjectKind> HiddenRunnableProjectKinds => EmptyList<RunnableProjectKind>.Instance;
}