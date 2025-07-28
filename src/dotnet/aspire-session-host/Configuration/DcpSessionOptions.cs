using Microsoft.Extensions.Options;

namespace JetBrains.Rider.Aspire.SessionHost.Configuration;

public class DcpSessionOptions
{
    public string? Token { get; set; }
}

internal sealed class ConfigureDcpSessionOptions(IConfiguration configuration)
    : IConfigureOptions<DcpSessionOptions>
{
    private const string SectionName = "DCP_SESSION";

    public void Configure(DcpSessionOptions options)
    {
        configuration.GetSection(SectionName).Bind(options);
    }
}