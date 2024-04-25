using Microsoft.Extensions.Options;

namespace AspireSessionHost.Resources;

public sealed class ResourceServiceOptions
{
    public string? ApiKey { get; set; }
};

internal sealed class ResourceServiceOptionSetup(IConfiguration configuration) : IConfigureOptions<ResourceServiceOptions>
{
    private const string SectionName = "ResourceService";

    public void Configure(ResourceServiceOptions options)
    {
        configuration.GetSection(SectionName).Bind(options);
    }
}