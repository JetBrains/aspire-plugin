using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.Sessions;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal interface IAspireHostService
{
    void AddNewHost(string id, AspireHostModel host, Lifetime lifetime);
    Task<(string? sessionId, ErrorResponse? error)?> CreateSession(string aspireHostId, Session session);
    Task<(string? sessionId, ErrorResponse? error)?> DeleteSession(string aspireHostId, string sessionId);
    ChannelReader<ISessionEvent>? GetSessionEventReader(string aspireHostId);
}