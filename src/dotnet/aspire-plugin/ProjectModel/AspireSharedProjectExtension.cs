using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.MSBuild;
using JetBrains.ProjectModel.ProjectsHost;
using JetBrains.ProjectModel.Properties;
using JetBrains.RdBackend.Common.Features.ProjectModel.View;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.ProjectModel;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireSharedProjectExtension : ProjectModelViewPresenterExtension
{
    public override bool TryAddUserData(IProjectMark projectMark, IProject? project, out string name, out string value)
    {
        var property = project?.GetUniqueRequestedProjectProperty(MSBuildProjectUtil.IsAspireSharedProjectProperty);
        if (property.IsNullOrEmpty())
        {
            return base.TryAddUserData(projectMark, project, out name, out value);
        }

        name = MSBuildProjectUtil.IsAspireSharedProjectProperty;
        value = property;

        return true;
    }
}