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
    private const string ApiKeyHeader = "x-resource-service-api-key";

    private readonly string _id;
    private readonly IRdConnectionWrapper _connectionWrapper;
    private readonly AspireHostModel _aspireHostModel;
    private readonly Channel<ISessionEvent> _sessionEventChannel = Channel.CreateUnbounded<ISessionEvent>();
    private readonly ResiliencePipelineProvider<string> _resiliencePipelineProvider;
    private readonly ILoggerFactory _loggerFactory;
    private readonly ILogger _logger;

    public ChannelReader<ISessionEvent> SessionEventReader => _sessionEventChannel.Reader;

    internal AspireHost(
        string id,
        IRdConnectionWrapper connectionWrapper,
        AspireHostModel model,
        ResiliencePipelineProvider<string> resiliencePipelineProvider,
        ILoggerFactory loggerFactory,
        Lifetime lifetime)
    {
        _id = id;
        _connectionWrapper = connectionWrapper;
        _aspireHostModel = model;
        _resiliencePipelineProvider = resiliencePipelineProvider;
        _loggerFactory = loggerFactory;
        _logger = loggerFactory.CreateLogger<AspireHost>();

        InitializeSessionEventWatcher(lifetime);

        var config = model.Config;
        if (!string.IsNullOrEmpty(config.ResourceServiceEndpointUrl))
        {
            InitializeResourceWatchers(config.ResourceServiceEndpointUrl, config.ResourceServiceApiKey, lifetime);
        }
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

    private void InitializeResourceWatchers(string resourceServiceEndpointUrl, string? resourceServiceApiKey,
        Lifetime lifetime)
    {
        _logger.ResourceWatchingIsEnabled(_id);

        var metadata = resourceServiceApiKey != null ? new Metadata { { ApiKeyHeader, resourceServiceApiKey } } : [];
        var client = CreateResourceClient(resourceServiceEndpointUrl, lifetime);

        _logger.CreatingResourceLogWatcher(_id);
        var resourceLogWatcherLogger = _loggerFactory.CreateLogger<AspireHostResourceLogWatcher>();
        var resourceLogWatcher = new AspireHostResourceLogWatcher(client, metadata, _connectionWrapper,
            _aspireHostModel, _resiliencePipelineProvider, resourceLogWatcherLogger, lifetime.CreateNested().Lifetime);
        lifetime.StartAttachedAsync(TaskScheduler.Default,
            async () => await resourceLogWatcher.WatchResourceLogs());

        _logger.CreatingResourceWatcher(_id);
        var resourceWatcherLogger = _loggerFactory.CreateLogger<AspireHostResourceWatcher>();
        var resourceWatcher = new AspireHostResourceWatcher(client, metadata, _connectionWrapper, _aspireHostModel,
            _resiliencePipelineProvider, resourceWatcherLogger, lifetime.CreateNested().Lifetime);
        lifetime.StartAttachedAsync(TaskScheduler.Default,
            async () => await resourceWatcher.WatchResources());
    }

    private static DashboardService.DashboardServiceClient CreateResourceClient(string resourceServiceEndpointUrl,
        Lifetime lifetime)
    {
        var retryPolicy = new MethodConfig
        {
            Names = { MethodName.Default },
            RetryPolicy = new RetryPolicy
            {
                MaxAttempts = 10,
                InitialBackoff = TimeSpan.FromSeconds(1),
                MaxBackoff = TimeSpan.FromSeconds(5),
                BackoffMultiplier = 1.5,
                RetryableStatusCodes = { StatusCode.Unavailable }
            }
        };

        var channel = GrpcChannel.ForAddress(
            resourceServiceEndpointUrl,
            new GrpcChannelOptions
            {
                ServiceConfig = new ServiceConfig { MethodConfigs = { retryPolicy } },
                ThrowOperationCanceledOnCancellation = true
            }
        );
        lifetime.AddDispose(channel);

        return new DashboardService.DashboardServiceClient(channel);
    }
}