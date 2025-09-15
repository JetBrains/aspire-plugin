using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostService(
    Connection connection,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    ILoggerFactory loggerFactory)
{
    private readonly Dictionary<string, AspireHost> _hosts = new();

    internal void AddNewHost(string id, AspireHostModel host, Lifetime lifetime)
    {
        var aspireHost = new AspireHost(
            id,
            connection,
            host,
            resiliencePipelineProvider,
            loggerFactory,
            lifetime);

        _hosts.AddLifetimed(lifetime, new KeyValuePair<string, AspireHost>(id, aspireHost));
    }

    internal AspireHost? GetAspireHost(string id) => _hosts.GetValueOrDefault(id);
}