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

    public async Task AdviceOnProcessStarted(AspireHostModel host, Lifetime lifetime, Action<ProcessStarted> action)
    {
        await rdConnection.DoWithModel(_ => host.ProcessStarted.Advise(lifetime, action));
    }

    public async Task AdviceOnProcessTerminated(AspireHostModel host, Lifetime lifetime, Action<ProcessTerminated> action)
    {
        await rdConnection.DoWithModel(_ => host.ProcessTerminated.Advise(lifetime, action));
    }

    public async Task AdviceOnLogReceived(AspireHostModel host, Lifetime lifetime, Action<LogReceived> action)
    {
        await rdConnection.DoWithModel(_ => host.LogReceived.Advise(lifetime, action));
    }

    public async Task AdviceOnMessageReceived(AspireHostModel host, Lifetime lifetime, Action<MessageReceived> action)
    {
        await rdConnection.DoWithModel(_ => host.MessageReceived.Advise(lifetime, action));
    }
}