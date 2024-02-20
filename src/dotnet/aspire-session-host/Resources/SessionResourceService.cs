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

    internal void Initialize()
    {
        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await WatchResources());
    }

    private async Task WatchResources()
    {
        var request = new WatchResourcesRequest { IsReconnect = false };
        var response = client.WatchResources(request, cancellationToken: Lifetime.AsyncLocal.Value);
        await foreach (var update in response.ResponseStream.ReadAllAsync(Lifetime.AsyncLocal.Value))
        {
            switch (update.KindCase)
            {
                case WatchResourcesUpdate.KindOneofCase.InitialData:
                    await HandleInitialData(update.InitialData, Lifetime.AsyncLocal.Value);
                    break;
                case WatchResourcesUpdate.KindOneofCase.Changes:
                    await HandleChanges(update.Changes, Lifetime.AsyncLocal.Value);
                    break;
            }
        }
    }

    private async Task HandleInitialData(InitialResourceData initialResourceData, CancellationToken ct)
    {
        await connection.DoWithModel(model => model.Resources.Clear());

        foreach (var resource in initialResourceData.Resources)
        {
            ct.ThrowIfCancellationRequested();

            var resourceModel = resource.ToModel();
            await connection.DoWithModel(model => model.Resources[resourceModel.Name] = resourceModel);
        }
    }

    private async Task HandleChanges(WatchResourcesChanges watchResourcesChanges, CancellationToken ct)
    {
        foreach (var change in watchResourcesChanges.Value)
        {
            ct.ThrowIfCancellationRequested();

            if (change is null) continue;

            switch (change.KindCase)
            {
                case WatchResourcesChange.KindOneofCase.Upsert:
                    var resourceModel = change.Upsert.ToModel();
                    await connection.DoWithModel(model => model.Resources[resourceModel.Name] = resourceModel);
                    break;
                case WatchResourcesChange.KindOneofCase.Delete:
                    await connection.DoWithModel(model => model.Resources.Remove(change.Delete.ResourceName));
                    break;
            }
        }
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}