using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.AspireHost;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.Sessions;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

internal class InMemoryAspireHostService : IAspireHostService
{
    private readonly Dictionary<string, (string HostId, Session Session)> _sessions = new();

    public void AddNewHost(string id, AspireHostModel host, Lifetime lifetime)
    {
    }

    public Task<(string? sessionId, ErrorResponse? error)?> CreateSession(string aspireHostId, Session session)
    {
        var sessionId = Guid.NewGuid().ToString();
        _sessions[sessionId] = (aspireHostId, session);
        return Task.FromResult<(string? sessionId, ErrorResponse? error)?>((sessionId, null));
    }

    public Task<(string? sessionId, ErrorResponse? error)?> DeleteSession(string aspireHostId, string sessionId)
    {
        var sessionWasRemoved = _sessions.Remove(sessionId);
        return sessionWasRemoved
            ? Task.FromResult<(string? sessionId, ErrorResponse? error)?>((sessionId, null))
            : Task.FromResult<(string? sessionId, ErrorResponse? error)?>(null);
    }

    public ChannelReader<ISessionEvent> GetSessionEventReader(string aspireHostId)
    {
        return Channel.CreateUnbounded<ISessionEvent>();
    }

    internal (string HostId, Session Session)? GetSession(string sessionId)
    {
        return _sessions.TryGetValue(sessionId, out var session) ?  session : null;
    }
}