using System.Collections.Generic;
using JetBrains.Application;
using JetBrains.ProjectModel.Properties;

namespace AspirePlugin.RunnableProject;

[ShellComponent]
public class AspireProjectPropertyRequest : IProjectPropertiesRequest
{
    public const string IsAspireHost = "IsAspireHost";

    public IEnumerable<string> RequestedProperties => new[]
    {
        IsAspireHost
    };
}