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

    private readonly Connection _connection;
    private readonly AspireHostModel _aspireHostModel;
    private readonly ILogger _logger;

    private readonly ErrorResponse _multipleProjectLaunchConfigurations = new(new ErrorDetail(
        "BadRequest",
        "Only a single launch configuration instance, of type project, can be used as part of a run session request."
    ));

    private readonly ErrorResponse _projectNotFound = new(new ErrorDetail(
        "NotFound",
        "A project file is not found."
    ));

    internal AspireHost(
        string id,
        Connection connection,
        AspireHostModel model,
        ResiliencePipelineProvider<string> resiliencePipelineProvider,
        ILoggerFactory loggerFactory,
        Lifetime lifetime)
    {
        _connection = connection;
        _aspireHostModel = model;
        _logger = loggerFactory.CreateLogger<AspireHost>();

        var config = model.Config;
        if (!string.IsNullOrEmpty(config.ResourceServiceEndpointUrl) &&
            !string.IsNullOrEmpty(config.ResourceServiceApiKey))
        {
            _logger.LogInformation("Resource watching is enabled for the host {id}", id);

            var metadata = new Metadata { { ApiKeyHeader, config.ResourceServiceApiKey } };
            var client = CreateResourceClient(config.ResourceServiceEndpointUrl, lifetime);

            var resourceLogWatcherLogger = loggerFactory.CreateLogger<AspireHostResourceLogWatcher>();
            var resourceLogWatcher = new AspireHostResourceLogWatcher(client, metadata, connection, model,
                resiliencePipelineProvider, resourceLogWatcherLogger, lifetime.CreateNested().Lifetime);
            lifetime.StartAttachedAsync(TaskScheduler.Default,
                async () => await resourceLogWatcher.WatchResourceLogs());

            var resourceWatcherLogger = loggerFactory.CreateLogger<AspireHostResourceWatcher>();
            var resourceWatcher = new AspireHostResourceWatcher(client, metadata, connection, model,
                resiliencePipelineProvider, resourceWatcherLogger);
            lifetime.StartAttachedAsync(TaskScheduler.Default,
                async () => await resourceWatcher.WatchResources());
        }
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
            ?.Select(it => new SessionEnvironmentVariable(it.Name, it.Value!))
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

        var result = await _connection.DoWithModel(model => _aspireHostModel.DeleteSession.Sync(request));
        _logger.LogDebug("Session deletion response: {sessionDeletionResponse}", result);

        var error = result.Error is not null ? BuildErrorResponse(result.Error) : null;

        return (result.SessionId, error);
    }

    private static ErrorResponse BuildErrorResponse(string message) => new(new ErrorDetail("BadRequest", message));
}