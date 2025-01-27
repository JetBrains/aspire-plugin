using Aspire.ResourceService.Proto.V1;
using Grpc.Core;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using Polly;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.SessionHost.AspireHost;

internal sealed class AspireHostResourceLogWatcher(
    DashboardService.DashboardServiceClient client,
    Metadata headers,
    Connection connection,
    AspireHostModel hostModel,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    ILogger logger,
    Lifetime lifetime)
{
    private readonly ResiliencePipeline _pipeline =
        resiliencePipelineProvider.GetPipeline(nameof(AspireHostResourceLogWatcher));

    internal async Task WatchResourceLogs()
    {
        await connection.DoWithModel(_ =>
        {
            hostModel.Resources.View(lifetime, (lt, resourceId, resource) =>
            {
                lt.StartAttachedAsync(
                    TaskScheduler.Default,
                    async () => await WatchResourceLogs(resourceId, resource)
                );
            });
        });
    }

    private async Task WatchResourceLogs(string resourceName, ResourceWrapper resource)
    {
        try
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
        catch (OperationCanceledException)
        {
            logger.LogInformation("Resource log watching was cancelled");
        }
    }

    private async Task<bool> SendWatchResourceLogsRequest(
        string resourceName,
        ResourceWrapper resource,
        CancellationToken ct)
    {
        logger.LogTrace("Sending log watching request for the resource {resourceName}", resourceName);

        var request = new WatchResourceConsoleLogsRequest { ResourceName = resourceName };
        var response = client.WatchResourceConsoleLogs(request, headers: headers, cancellationToken: ct);
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
}