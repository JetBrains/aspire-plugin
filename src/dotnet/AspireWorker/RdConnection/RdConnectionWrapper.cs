using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rd.Base;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

/// <summary>
/// Represents a wrapper class for managing operations with the <c>RdConnection</c>.
/// Provides methods for interaction with <c>AspireHostModel</c>, including session management,
/// resource management, and listening to events related to session processes and resource logs.
/// </summary>
internal sealed class RdConnectionWrapper(RdConnection rdConnection) : IRdConnectionWrapper
{
    public async Task ViewHosts(Lifetime lifetime, Action<Lifetime, string, AspireHostModel> action)
    {
        await rdConnection.DoWithModel(model => model.AspireHosts.View(lifetime, action));
    }

    public async Task<CreateSessionResponse?> CreateSession(CreateSessionRequest request)
    {
        return await rdConnection.DoWithModel(model => model.CreateSession.Sync(request));
    }

    public async Task<DeleteSessionResponse?> DeleteSession(DeleteSessionRequest request)
    {
        return await rdConnection.DoWithModel(model => model.DeleteSession.Sync(request));
    }

    public async Task AdviceOnProcessStarted(Lifetime lifetime, Action<ProcessStarted> action)
    {
        await rdConnection.DoWithModel(model => model.ProcessStarted.Advise(lifetime, action));
    }

    public async Task AdviceOnProcessTerminated(Lifetime lifetime, Action<ProcessTerminated> action)
    {
        await rdConnection.DoWithModel(model => model.ProcessTerminated.Advise(lifetime, action));
    }

    public async Task AdviceOnLogReceived(Lifetime lifetime, Action<LogReceived> action)
    {
        await rdConnection.DoWithModel(model => model.LogReceived.Advise(lifetime, action));
    }

    public async Task AdviceOnMessageReceived(Lifetime lifetime, Action<MessageReceived> action)
    {
        await rdConnection.DoWithModel(model => model.MessageReceived.Advise(lifetime, action));
    }

    public async Task<bool> AddResource(AspireHostModel host, string resourceName, ResourceWrapper resourceWrapper)
    {
        return await rdConnection.DoWithModel(_ => host.Resources.TryAdd(resourceName, resourceWrapper));
    }

    public async Task UpsertResource(AspireHostModel host, ResourceModel resourceModel,
        Func<ResourceModel, ResourceWrapper> resourceWrapperFactory)
    {
        await rdConnection.DoWithModel(_ =>
        {
            if (host.Resources.ContainsKey(resourceModel.Name))
            {
                host.Resources[resourceModel.Name].Model.SetValue(resourceModel);
            }
            else
            {
                var resourceWrapper = resourceWrapperFactory(resourceModel);
                host.Resources.TryAdd(resourceModel.Name, resourceWrapper);
            }
        });
    }

    public async Task<bool> RemoveResource(AspireHostModel host, string resourceName)
    {
        return await rdConnection.DoWithModel(_ => host.Resources.Remove(resourceName));
    }

    public async Task ClearResources(AspireHostModel host)
    {
        await rdConnection.DoWithModel(_ => host.Resources.Clear());
    }

    public async Task ViewResources(AspireHostModel host, Lifetime lifetime,
        Action<Lifetime, string, ResourceWrapper> action)
    {
        await rdConnection.DoWithModel(_ => { host.Resources.View(lifetime, action); });
    }

    public async Task ResourceLogReceived(ResourceWrapper resource, ResourceLog resourceLog)
    {
        await rdConnection.DoWithModel(_ => resource.LogReceived(resourceLog));
    }
}