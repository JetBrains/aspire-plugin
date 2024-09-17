using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Options;

namespace JetBrains.Rider.Aspire.SessionHost.Resources;

public sealed class ResourceServiceOptions
{
    public string? EndpointUrl { get; set; }
    public string? ApiKey { get; set; }
};

internal sealed class ConfigureResourceServiceOptions(IConfiguration configuration) : IConfigureOptions<ResourceServiceOptions>
{
    public const string SectionName = "ResourceService";

    public void Configure(ResourceServiceOptions options)
    {
        configuration.GetSection(SectionName).Bind(options);
    }
}