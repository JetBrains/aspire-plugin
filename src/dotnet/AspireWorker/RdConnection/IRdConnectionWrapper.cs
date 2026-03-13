using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

/// <summary>
/// Provides an abstraction for managing operations via RD-protocol.
/// This interface defines methods for session management, resource management
/// as well as subscribing to or handling events related to session processes and resource logs.
/// </summary>
internal interface IRdConnectionWrapper
{
    Task ViewHosts(Lifetime lifetime, Action<Lifetime, string, AspireHostModel> action);

    Task<CreateSessionResponse?> CreateSession(CreateSessionRequest request);
    Task<DeleteSessionResponse?> DeleteSession(DeleteSessionRequest request);

    Task AdviceOnProcessStarted(Lifetime lifetime, Action<ProcessStarted> action);
    Task AdviceOnProcessTerminated(Lifetime lifetime, Action<ProcessTerminated> action);
    Task AdviceOnLogReceived(Lifetime lifetime, Action<LogReceived> action);
    Task AdviceOnMessageReceived(Lifetime lifetime, Action<MessageReceived> action);

    Task<bool> AddResource(AspireHostModel host, string resourceName, ResourceWrapper resourceWrapper);
    Task UpsertResource(AspireHostModel host, ResourceModel resourceModel,
        Func<ResourceModel, ResourceWrapper> resourceWrapperFactory);
    Task<bool> RemoveResource(AspireHostModel host, string resourceName);
    Task ClearResources(AspireHostModel host);
    Task ViewResources(AspireHostModel host, Lifetime lifetime,
        Action<Lifetime, string, ResourceWrapper> action);

    Task ResourceLogReceived(ResourceWrapper resource, ResourceLog resourceLog);
}