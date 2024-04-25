using AspireSessionHost.Generated;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rd;
using JetBrains.Rd.Impl;

namespace AspireSessionHost;

internal sealed class Connection : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();
    private readonly Lifetime _lifetime;
    private readonly IScheduler _scheduler;
    private readonly IProtocol _protocol;
    private readonly Task<AspireSessionHostModel> _model;

    internal Connection(ConfigurationManager configuration)
    {
        var connectionOptions = configuration
            .GetSection(ConnectionOptions.SectionName)
            .Get<ConnectionOptions>();
        if (connectionOptions?.RdPort == null)
            throw new ApplicationException("Unable to find RD port environment variable");

        _lifetime = _lifetimeDef.Lifetime;
        _scheduler = SingleThreadScheduler.RunOnSeparateThread(_lifetime, "AspireSessionHost Protocol Connection");
        var wire = new SocketWire.Client(_lifetime, _scheduler, connectionOptions.RdPort.Value);
        _protocol = new Protocol(
            "AspireSessionHost Protocol",
            new Serializers(),
            new Identities(IdKind.Client),
            _scheduler,
            wire,
            _lifetime
        );
        _model = InitializeModelAsync();
    }

    private Task<AspireSessionHostModel> InitializeModelAsync()
    {
        var tcs = new TaskCompletionSource<AspireSessionHostModel>(TaskCreationOptions.RunContinuationsAsynchronously);
        _scheduler.Queue(() =>
        {
            try
            {
                tcs.SetResult(new AspireSessionHostModel(_lifetime, _protocol));
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        });
        return tcs.Task;
    }

    internal async Task<T> DoWithModel<T>(Func<AspireSessionHostModel, T> action)
    {
        var model = await _model;
        var tcs = new TaskCompletionSource<T>(TaskCreationOptions.RunContinuationsAsynchronously);
        _scheduler.Queue(() =>
        {
            try
            {
                tcs.SetResult(action(model));
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        });
        return await tcs.Task;
    }

    internal Task DoWithModel(Action<AspireSessionHostModel> action) =>
        DoWithModel(model =>
        {
            action(model);
            return 0;
        });

    public void Dispose()
    {
        _lifetimeDef.Terminate();
    }
}