using System.Threading.Channels;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using JetBrains.Rider.Aspire.Worker.Sessions;
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

    internal async Task<(string? sessionId, ErrorResponse? error)?> CreateSession(string aspireHostId, Session session)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);
        if (aspireHost is null)
        {
            return null;
        }

        return await aspireHost.Create(session);
    }

    internal async Task<(string? sessionId, ErrorResponse? error)?> DeleteSession(string aspireHostId, string sessionId)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);
        if (aspireHost is null)
        {
            return null;
        }

        return await aspireHost.Delete(sessionId);
    }

    internal ChannelReader<ISessionEvent>? GetSessionEventReader(string aspireHostId)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);

        return aspireHost?.SessionEventReader;
    }
}