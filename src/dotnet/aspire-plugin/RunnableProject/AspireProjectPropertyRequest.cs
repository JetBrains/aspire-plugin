using System.Collections.Generic;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Properties;

namespace AspirePlugin.RunnableProject;

[SolutionComponent]
public class AspireProjectPropertyRequest : IProjectPropertiesRequest
{
    public const string IsAspireHost = "IsAspireHost";

    public IEnumerable<string> RequestedProperties => new[]
    {
        IsAspireHost
    };
}