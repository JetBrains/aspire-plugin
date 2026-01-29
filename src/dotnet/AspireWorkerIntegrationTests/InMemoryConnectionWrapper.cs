using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

internal sealed class InMemoryConnectionWrapper : IRdConnectionWrapper, IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();
    private readonly List<Action<Lifetime, string, AspireHostModel>> _hostViewers = [];
    private readonly List<(string SessionId, CreateSessionRequest SessionRequest)> _createSessionRequests = [];
    private readonly List<(string SessionId, DeleteSessionRequest SessionRequest)> _deletedSessionRequests = [];

    internal void AddHost(string id, AspireHostModel host)
    {
        foreach (var viewer in _hostViewers)
        {
            viewer.Invoke(_lifetimeDef.Lifetime, id, host);
        }
    }

    internal List<CreateSessionRequest> GetCreateSessionRequestsById(string sessionId) => _createSessionRequests
        .Where(it => it.SessionId == sessionId)
        .Select(it => it.SessionRequest)
        .ToList();

    internal List<DeleteSessionRequest> GetDeletedSessionRequestsById(string sessionId) => _deletedSessionRequests
        .Where(it => it.SessionId == sessionId)
        .Select(it => it.SessionRequest)
        .ToList();

    public Task ViewHosts(Lifetime lifetime, Action<Lifetime, string, AspireHostModel> action)
    {
        _hostViewers.Add(action);
        return Task.CompletedTask;
    }

    public Task<CreateSessionResponse?> CreateSession(AspireHostModel host, CreateSessionRequest request)
    {
        var sessionId = Guid.NewGuid().ToString();
        _createSessionRequests.Add((sessionId, request));
        var response = new CreateSessionResponse(sessionId, null);
        return Task.FromResult(response);
    }

    public Task<DeleteSessionResponse?> DeleteSession(AspireHostModel host, DeleteSessionRequest request)
    {
        var sessionId = request.SessionId;
        _deletedSessionRequests.Add((sessionId, request));
        var response = new DeleteSessionResponse(sessionId, null);
        return Task.FromResult(response);
    }

    public Task AdviceOnProcessStarted(AspireHostModel host, Lifetime lifetime, Action<ProcessStarted> action)
    {
        return Task.CompletedTask;
    }

    public Task AdviceOnProcessTerminated(AspireHostModel host, Lifetime lifetime, Action<ProcessTerminated> action)
    {
        return Task.CompletedTask;
    }

    public Task AdviceOnLogReceived(AspireHostModel host, Lifetime lifetime, Action<LogReceived> action)
    {
        return Task.CompletedTask;
    }

    public Task AdviceOnMessageReceived(AspireHostModel host, Lifetime lifetime, Action<MessageReceived> action)
    {
        return Task.CompletedTask;
    }

    public Task<bool> AddResource(AspireHostModel host, string resourceName, ResourceWrapper resourceWrapper)
    {
        return Task.FromResult(true);
    }

    public Task UpsertResource(AspireHostModel host, ResourceModel resourceModel,
        Func<ResourceModel, ResourceWrapper> resourceWrapperFactory)
    {
        return Task.CompletedTask;
    }

    public Task<bool> RemoveResource(AspireHostModel host, string resourceName)
    {
        return Task.FromResult(true);
    }

    public Task ClearResources(AspireHostModel host)
    {
        return Task.CompletedTask;
    }

    public Task ViewResources(AspireHostModel host, Lifetime lifetime, Action<Lifetime, string, ResourceWrapper> action)
    {
        return Task.CompletedTask;
    }

    public Task ResourceLogReceived(ResourceWrapper resource, ResourceLog resourceLog)
    {
        return Task.CompletedTask;
    }

    public void Dispose()
    {
        _lifetimeDef.Terminate();
    }
}