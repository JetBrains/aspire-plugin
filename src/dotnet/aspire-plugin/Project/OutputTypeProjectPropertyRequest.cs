using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel.Properties;

namespace JetBrains.Rider.Aspire.Project;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class OutputTypeProjectPropertyRequest : IProjectPropertiesRequest
{
    public const string OutputType = "OutputType";
    public IEnumerable<string> RequestedProperties => [OutputType];
}