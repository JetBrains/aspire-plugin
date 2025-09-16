using Aspire.DashboardService.Proto.V1;
using Grpc.Core;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using Polly;
using Polly.Registry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostResourceLogWatcher(
    DashboardService.DashboardServiceClient client,
    Metadata headers,
    RdConnection.RdConnection connection,
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
                    async () => await WatchResourceLogs(resourceId, resource, lt)
                );
            });
        });
    }

    private async Task WatchResourceLogs(string resourceName, ResourceWrapper resource, Lifetime resourceLifetime)
    {
        try
        {
            logger.StartLogWatchingForResource(resourceName);

            if (!resource.IsInitialized.HasTrueValue())
            {
                await resource.IsInitialized.NextTrueValueAsync(resourceLifetime);
            }

            await Task.Delay(TimeSpan.FromSeconds(5), resourceLifetime);

            await _pipeline.ExecuteAsync(
                async token => await SendWatchResourceLogsRequest(resourceName, resource, token), resourceLifetime);

            logger.StopLogWatchingForResource(resourceName, resourceLifetime.IsAlive);
        }
        catch (OperationCanceledException)
        {
            logger.ResourceLogWatchingWasCancelled();
        }
    }

    private async Task SendWatchResourceLogsRequest(
        string resourceName,
        ResourceWrapper resource,
        CancellationToken ct)
    {
        logger.SendingLogWatchingRequestForResource(resourceName);

        try
        {
            var request = new WatchResourceConsoleLogsRequest { ResourceName = resourceName };
            var response = client.WatchResourceConsoleLogs(request, headers: headers, cancellationToken: ct);
            await foreach (var update in response.ResponseStream.ReadAllAsync(ct))
            {
                foreach (var logLine in update.LogLines)
                {
                    ct.ThrowIfCancellationRequested();

                    if (string.IsNullOrEmpty(logLine.Text)) continue;

                    logger.ResourceLogLineReceived(logLine);
                    await connection.DoWithModel(_ =>
                        resource.LogReceived(
                            new ResourceLog(
                                logLine.Text,
                                // ReSharper disable once SimplifyConditionalTernaryExpression
                                logLine.HasIsStdErr ? logLine.IsStdErr : false,
                                logLine.LineNumber
                            )
                        )
                    );
                }
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            logger.LogWatchingRequestWasCancelled();
        }
    }
}