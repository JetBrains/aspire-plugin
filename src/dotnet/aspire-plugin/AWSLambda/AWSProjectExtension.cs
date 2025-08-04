using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.ProjectsHost;
using JetBrains.ProjectModel.Properties;
using JetBrains.RdBackend.Common.Features.ProjectModel.View;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.AWSLambda;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class AWSProjectExtension: ProjectModelViewPresenterExtension
{
    public override bool TryAddUserData(IProjectMark projectMark, IProject? project, out string name, out string value)
    {
        var property = project?.GetUniqueRequestedProjectProperty(AWSProjectPropertyRequest.AWSProjectType);
        if (property.IsNullOrEmpty())
        {
            return base.TryAddUserData(projectMark, project, out name, out value);
        }

        name = AWSProjectPropertyRequest.AWSProjectType;
        value = property;

        return true;
    }
}