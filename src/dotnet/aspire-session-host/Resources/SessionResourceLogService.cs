using Aspire.V1;
using AspireSessionHost.Generated;
using Grpc.Core;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;

namespace AspireSessionHost.Resources;

internal sealed class SessionResourceLogService(
    Connection connection,
    DashboardService.DashboardServiceClient client
) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    internal async Task Initialize()
    {
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
        var request = new WatchResourceConsoleLogsRequest { ResourceName = resourceName };
        var response = client.WatchResourceConsoleLogs(request, cancellationToken: Lifetime.AsyncLocal.Value);
        await foreach (var update in response.ResponseStream.ReadAllAsync(Lifetime.AsyncLocal.Value))
        {
            foreach (var logLine in update.LogLines)
            {
                await connection.DoWithModel(_ =>
                    resource.LogReceived(new ResourceLog(logLine.Text, logLine.HasIsStdErr ? logLine.IsStdErr : false))
                );
            }
        }
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}