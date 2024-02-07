using JetBrains.Application;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.ProjectsHost;
using JetBrains.ProjectModel.Properties;
using JetBrains.RdBackend.Common.Features.ProjectModel.View;
using JetBrains.Util;

namespace AspirePlugin.Project;

[ShellComponent]
public class AspireHostExtension : ProjectModelViewPresenterExtension
{
    public override bool TryAddUserData(IProjectMark projectMark, IProject? project, out string name, out string value)
    {
        var property = project?.GetUniqueRequestedProjectProperty(AspireHostProjectPropertyRequest.IsAspireHost);
        if (property.IsNullOrEmpty())
        {
            return base.TryAddUserData(projectMark, project, out name, out value);
        }

        name = AspireHostProjectPropertyRequest.IsAspireHost;
        value = property;

        return true;
    }
}