using System.Threading.Channels;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostService(
    IRdConnectionWrapper connectionWrapper,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    ILoggerFactory loggerFactory
) : IAspireHostService
{
    private readonly Dictionary<string, AspireHost> _hosts = new();

    public void AddNewHost(string id, AspireHostModel host, Lifetime lifetime)
    {
        var aspireHost = new AspireHost(
            id,
            connectionWrapper,
            host,
            resiliencePipelineProvider,
            loggerFactory,
            lifetime);

        _hosts.AddLifetimed(lifetime, new KeyValuePair<string, AspireHost>(id, aspireHost));
    }

    public async Task<(string? sessionId, Errors.IError? error)> CreateSession(string aspireHostId, Session session)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);
        if (aspireHost is null)
        {
            return (null, Errors.AspireHostNotFound);
        }

        return await aspireHost.Create(session);
    }

    public async Task<(string? sessionId, Errors.IError? error)> DeleteSession(string aspireHostId, string sessionId)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);
        if (aspireHost is null)
        {
            return (null, Errors.AspireHostNotFound);
        }

        return await aspireHost.Delete(sessionId);
    }

    public ChannelReader<ISessionEvent>? GetSessionEventReader(string aspireHostId)
    {
        var aspireHost = _hosts.GetValueOrDefault(aspireHostId);

        return aspireHost?.SessionEventReader;
    }
}