using System.Threading.Channels;
using Aspire.DashboardService.Proto.V1;
using Grpc.Core;
using Grpc.Net.Client;
using Grpc.Net.Client.Configuration;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHost
{
    private readonly IRdConnectionWrapper _connectionWrapper;
    private readonly AspireHostModel _aspireHostModel;
    private readonly Channel<ISessionEvent> _sessionEventChannel = Channel.CreateUnbounded<ISessionEvent>();
    private readonly ILoggerFactory _loggerFactory;

    public ChannelReader<ISessionEvent> SessionEventReader => _sessionEventChannel.Reader;

    internal AspireHost(
        IRdConnectionWrapper connectionWrapper,
        AspireHostModel model,
        ILoggerFactory loggerFactory,
        Lifetime lifetime)
    {
        _connectionWrapper = connectionWrapper;
        _aspireHostModel = model;
        _loggerFactory = loggerFactory;

        InitializeSessionEventWatcher(lifetime);
    }

    private void InitializeSessionEventWatcher(Lifetime lifetime)
    {
        var sessionEventWatcherLogger = _loggerFactory.CreateLogger<SessionEventWatcher>();
        var sessionEventWatcher = new SessionEventWatcher(
            _connectionWrapper,
            _aspireHostModel,
            _sessionEventChannel.Writer,
            sessionEventWatcherLogger,
            lifetime.CreateNested().Lifetime);
        lifetime.StartAttachedAsync(TaskScheduler.Default,
            async () => await sessionEventWatcher.WatchSessionEvents());
    }
}