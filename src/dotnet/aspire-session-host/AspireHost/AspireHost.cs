using System.Threading.Channels;
using Aspire.ResourceService.Proto.V1;
using Grpc.Core;
using Grpc.Net.Client;
using Grpc.Net.Client.Configuration;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using JetBrains.Rider.Aspire.SessionHost.Sessions;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.SessionHost.AspireHost;

internal sealed class AspireHost
{
    private const string ApiKeyHeader = "x-resource-service-api-key";

    private readonly string _id;
    private readonly Connection _connection;
    private readonly AspireHostModel _aspireHostModel;
    private readonly ILoggerFactory _loggerFactory;
    private readonly ILogger _logger;
    private readonly ResiliencePipelineProvider<string> _resiliencePipelineProvider;

    private readonly ErrorResponse _multipleProjectLaunchConfigurations = new(new ErrorDetail(
        "BadRequest",
        "Only a single launch configuration instance, of type project, can be used as part of a run session request."
    ));

    private readonly ErrorResponse _projectNotFound = new(new ErrorDetail(
        "NotFound",
        "A project file is not found."
    ));

    private readonly Channel<ISessionEvent> _sessionEventChannel = Channel.CreateUnbounded<ISessionEvent>(
        new UnboundedChannelOptions
        {
            SingleReader = true,
            SingleWriter = true
        });

    internal ChannelReader<ISessionEvent> SessionEventReader => _sessionEventChannel.Reader;

    internal AspireHost(
        string id,
        Connection connection,
        AspireHostModel model,
        ResiliencePipelineProvider<string> resiliencePipelineProvider,
        ILoggerFactory loggerFactory,
        Lifetime lifetime)
    {
        _id = id;
        _connection = connection;
        _aspireHostModel = model;
        _loggerFactory = loggerFactory;
        _logger = loggerFactory.CreateLogger<AspireHost>();
        _resiliencePipelineProvider = resiliencePipelineProvider;

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
        var sessionEventWatcher = new SessionEventWatcher(_connection, _aspireHostModel, _sessionEventChannel.Writer,
            sessionEventWatcherLogger, lifetime.CreateNested().Lifetime);
        lifetime.StartAttachedAsync(TaskScheduler.Default,
            async () => await sessionEventWatcher.WatchSessionEvents());
    }

    private void InitializeResourceWatchers(string resourceServiceEndpointUrl, string? resourceServiceApiKey,
        Lifetime lifetime)
    {
        _logger.LogInformation("Resource watching is enabled for the host {aspireHostId}", _id);

        var metadata = resourceServiceApiKey != null ? new Metadata { { ApiKeyHeader, resourceServiceApiKey } } : [];
        var client = CreateResourceClient(resourceServiceEndpointUrl, lifetime);

        _logger.LogDebug("Creating resource log watcher for {aspireHostId}", _id);
        var resourceLogWatcherLogger = _loggerFactory.CreateLogger<AspireHostResourceLogWatcher>();
        var resourceLogWatcher = new AspireHostResourceLogWatcher(client, metadata, _connection, _aspireHostModel,
            _resiliencePipelineProvider, resourceLogWatcherLogger, lifetime.CreateNested().Lifetime);
        lifetime.StartAttachedAsync(TaskScheduler.Default,
            async () => await resourceLogWatcher.WatchResourceLogs());

        _logger.LogDebug("Creating resource watcher for {aspireHostId}", _id);
        var resourceWatcherLogger = _loggerFactory.CreateLogger<AspireHostResourceWatcher>();
        var resourceWatcher = new AspireHostResourceWatcher(client, metadata, _connection, _aspireHostModel,
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

    internal async Task<(string? sessionId, ErrorResponse? error)> Create(Session session)
    {
        var launchConfiguration = session.LaunchConfigurations.SingleOrDefault(it =>
            string.Equals(it.Type, "project", StringComparison.InvariantCultureIgnoreCase)
        );
        if (launchConfiguration == null)
        {
            _logger.LogWarning("Only a single project launch configuration is supported.");
            return (null, _multipleProjectLaunchConfigurations);
        }

        if (!File.Exists(launchConfiguration.ProjectPath))
        {
            _logger.LogWarning("Project file doesn't exist");
            return (null, _projectNotFound);
        }

        var envs = session.Env
            ?.Where(it => it.Value is not null)
            // ReSharper disable once ConditionalAccessQualifierIsNonNullableAccordingToAPIContract
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
            // ReSharper disable once ConditionalAccessQualifierIsNonNullableAccordingToAPIContract
            ?.ToArray();

        var request = new CreateSessionRequest(
            launchConfiguration.ProjectPath,
            launchConfiguration.Mode == Mode.Debug,
            launchConfiguration.LaunchProfile,
            launchConfiguration.DisableLaunchProfile == true,
            session.Args,
            envs
        );

        _logger.LogInformation("Creating a new session {createSessionRequest}", request);

        var result = await _connection.DoWithModel(_ => _aspireHostModel.CreateSession.Sync(request));
        _logger.LogDebug("Session creation response: {sessionCreationResponse}", result);

        var error = result.Error is not null ? BuildErrorResponse(result.Error) : null;

        return (result.SessionId, error);
    }

    internal async Task<(string? sessionId, ErrorResponse? error)> Delete(string id)
    {
        var request = new DeleteSessionRequest(id);

        _logger.LogInformation("Deleting the session {deleteSessionRequest}", request);

        var result = await _connection.DoWithModel(_ => _aspireHostModel.DeleteSession.Sync(request));
        _logger.LogDebug("Session deletion response: {sessionDeletionResponse}", result);

        var error = result.Error is not null ? BuildErrorResponse(result.Error) : null;

        return (result.SessionId, error);
    }

    private static ErrorResponse BuildErrorResponse(string message) => new(new ErrorDetail("BadRequest", message));
}