using System.Collections.Generic;
using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel.Properties;

namespace JetBrains.Rider.Aspire.Project;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireHostProjectPropertyRequest : IProjectPropertiesRequest
{
    public const string IsAspireHost = "IsAspireHost";
    public IEnumerable<string> RequestedProperties => new[] { IsAspireHost };
}