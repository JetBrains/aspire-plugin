using Aspire.V1;
using JetBrains.Lifetimes;

namespace AspireSessionHost.Resources;

internal sealed class ResourceService(Connection connection, DashboardService.DashboardServiceClient client) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    internal async Task Subscribe()
    {
        // client.WatchResources(new WatchResourcesRequest())
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}