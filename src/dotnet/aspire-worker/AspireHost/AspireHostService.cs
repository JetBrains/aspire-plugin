using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostService(
    Connection connection,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    ILoggerFactory loggerFactory) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();
    private readonly Dictionary<string, AspireHost> _hosts = new();

    internal async Task Initialize()
    {
        if (!connection.IsConnected)
            return;

        await connection.DoWithModel(model =>
        {
            model.AspireHosts.View(_lifetimeDef.Lifetime, (lifetime, id, host) =>
            {
                var aspireHost = new AspireHost(id, connection, host, resiliencePipelineProvider, loggerFactory,
                    lifetime);
                _hosts.AddLifetimed(lifetime, new KeyValuePair<string, AspireHost>(id, aspireHost));
            });
        });
    }

    internal AspireHost? GetAspireHost(string id) => _hosts.GetValueOrDefault(id);

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}