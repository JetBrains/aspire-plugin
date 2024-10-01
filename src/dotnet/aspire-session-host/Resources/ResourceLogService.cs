using Aspire.ResourceService.Proto.V1;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using Grpc.Core;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using Microsoft.Extensions.Options;
using Polly;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.SessionHost.Resources;

internal sealed class ResourceLogService(
    Connection connection,
    DashboardService.DashboardServiceClient client,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    IOptions<ResourceServiceOptions> options,
    ILogger<ResourceLogService> logger
) : IDisposable
{
    private const string ApiKeyHeader = "x-resource-service-api-key";
    private readonly Metadata _headers = [];
    private readonly ResourceServiceOptions _optionValue = options.Value;
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ResiliencePipeline _pipeline =
        resiliencePipelineProvider.GetPipeline(nameof(ResourceLogService));

    internal async Task Initialize()
    {
        if (_optionValue.ApiKey is not null)
        {
            _headers.Add(ApiKeyHeader, _optionValue.ApiKey);
        }

        await connection.DoWithModel(model =>
        {
            model.Resources.View(_lifetimeDef.Lifetime, (lifetime, resourceId, resource) =>
            {
                lifetime.StartAttachedAsync(
                    TaskScheduler.Default,
                    async () => await WatchResourceLogs(resourceId, resource)
                );
            });
        });
    }

    private async Task WatchResourceLogs(string resourceName, ResourceWrapper resource)
    {
        logger.LogInformation("Start log watching for the resource {resourceName}", resourceName);

        if (!resource.IsInitialized.HasTrueValue())
        {
            await resource.IsInitialized.NextTrueValueAsync(Lifetime.AsyncLocal.Value);
        }

        await Task.Delay(TimeSpan.FromSeconds(5), Lifetime.AsyncLocal.Value);

        await _pipeline.ExecuteAsync(
            async token => await SendWatchResourceLogsRequest(resourceName, resource, token),
            Lifetime.AsyncLocal.Value
        );

        logger.LogInformation("Stop log watching for the resource {resourceName}, lifetime is alive {isAlive}",
            resourceName, Lifetime.AsyncLocal.Value.IsAlive);
    }

    private async Task<bool> SendWatchResourceLogsRequest(
        string resourceName,
        ResourceWrapper resource,
        CancellationToken ct)
    {
        logger.LogTrace("Sending log watching request for the resource {resourceName}", resourceName);

        var request = new WatchResourceConsoleLogsRequest { ResourceName = resourceName };
        var response = client.WatchResourceConsoleLogs(request, headers: _headers, cancellationToken: ct);
        await foreach (var update in response.ResponseStream.ReadAllAsync(ct))
        {
            foreach (var logLine in update.LogLines)
            {
                ct.ThrowIfCancellationRequested();

                if (string.IsNullOrEmpty(logLine.Text)) continue;

                logger.LogTrace("Log line received {logLine}", logLine);
                await connection.DoWithModel(_ =>
                    resource.LogReceived(
                        new ResourceLog(
                            logLine.Text,
                            logLine.HasIsStdErr ? logLine.IsStdErr : false,
                            logLine.LineNumber
                        )
                    )
                );
            }
        }

        //Sometimes Aspire returns just an empty list, and the method simply quits without sending any log.
        //In such a case, we want to retry the method.
        //So, only if the corresponding token has been canceled, we should stop execution.
        return ct.IsCancellationRequested;
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}