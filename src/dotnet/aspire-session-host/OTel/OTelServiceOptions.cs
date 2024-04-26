using Microsoft.Extensions.Options;

namespace AspireSessionHost.OTel;

public class OTelServiceOptions
{
    public string? EndpointUrl { get; set; }
    public int? ServerPort { get; set; }
}

internal sealed class ConfigureOTelServiceOptions(IConfiguration configuration) : IConfigureOptions<OTelServiceOptions>
{
    public const string SectionName = "OtelService";

    public void Configure(OTelServiceOptions options)
    {
        configuration.GetSection(SectionName).Bind(options);
    }
}