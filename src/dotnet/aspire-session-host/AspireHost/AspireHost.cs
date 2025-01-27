using Aspire.ResourceService.Proto.V1;
using Grpc.Core;
using Grpc.Net.Client;
using Grpc.Net.Client.Configuration;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.SessionHost.AspireHost;

internal sealed class AspireHost
{
    private const string ApiKeyHeader = "x-resource-service-api-key";

    internal AspireHost(string id, Connection connection, AspireHostModel model,
        ResiliencePipelineProvider<string> resiliencePipelineProvider, ILoggerFactory loggerFactory, Lifetime lifetime)
    {
        var logger = loggerFactory.CreateLogger<AspireHost>();

        var config = model.Config;
        if (!string.IsNullOrEmpty(config.ResourceServiceEndpointUrl) &&
            !string.IsNullOrEmpty(config.ResourceServiceApiKey))
        {
            logger.LogInformation("Resource watching is enabled for the host {id}", id);

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
}