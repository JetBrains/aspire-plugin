using JetBrains.Application;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel.Properties;

namespace JetBrains.Rider.Aspire.AWSLambda;

[ShellComponent(Instantiation.DemandAnyThreadSafe)]
public class AWSProjectPropertyRequest : IProjectPropertiesRequest
{
    public const string AWSProjectType = "AWSProjectType";
    public IEnumerable<string> RequestedProperties => [AWSProjectType];
}