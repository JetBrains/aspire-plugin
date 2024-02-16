using System.Diagnostics;
using Aspire.V1;
using Grpc.Core;
using JetBrains.Lifetimes;

#pragma warning disable CS4014 // Because this call is not awaited, execution of the current method continues before the call is completed

namespace AspireSessionHost.Resources;

internal sealed class SessionResourceService(
    Connection connection,
    DashboardService.DashboardServiceClient client
) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    internal async Task Initialize()
    {
        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await WatchResources());
    }

    private async Task WatchResources()
    {
        var request = new WatchResourcesRequest { IsReconnect = false };
        var response = client.WatchResources(request, cancellationToken: Lifetime.AsyncLocal.Value);
        await foreach (var update in response.ResponseStream.ReadAllAsync(Lifetime.AsyncLocal.Value))
        {
        }
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}