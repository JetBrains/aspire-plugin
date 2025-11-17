using Microsoft.Extensions.Options;

namespace JetBrains.Rider.Aspire.Worker.Configuration;

public class ConnectionOptions
{
    public int? RdPort { get; set; }
}

internal sealed class ConfigureConnectionOptions(IConfiguration configuration)
    : IConfigureOptions<ConnectionOptions>
{
    private const string SectionName = "CONNECTION";

    public void Configure(ConnectionOptions options)
    {
        configuration.GetSection(SectionName).Bind(options);
    }
}