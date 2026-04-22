using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostService(
    IRdConnectionWrapper connectionWrapper,
    ILoggerFactory loggerFactory
) : IAspireHostService
{
    private readonly Dictionary<string, AspireHost> _hosts = new();

    public void AddNewHost(string id, AspireHostModel host, Lifetime lifetime)
    {
        var aspireHost = new AspireHost(
            connectionWrapper,
            host,
            loggerFactory,
            lifetime);

        _hosts.AddLifetimed(lifetime, new KeyValuePair<string, AspireHost>(id, aspireHost));
    }

    public AspireHost? GetHost(string id) => _hosts.TryGetValue(id, out var aspireHost) ? aspireHost : null;
}