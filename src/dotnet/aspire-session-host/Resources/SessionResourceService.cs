using Aspire.V1;
using AspireSessionHost.Generated;
using Grpc.Core;
using JetBrains.Lifetimes;
using JetBrains.Rd.Base;

namespace AspireSessionHost.Resources;

internal sealed class SessionResourceService(
    Connection connection,
    DashboardService.DashboardServiceClient client,
    ILogger<SessionResourceService> logger
) : IDisposable
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    internal void Initialize()
    {
        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await WatchResources());
    }

    private async Task WatchResources()
    {
        logger.LogInformation("Start resource watching");

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

        logger.LogInformation("Stop resource watching, lifetime is alive {isAlive}", Lifetime.AsyncLocal.Value.IsAlive);
    }

    private async Task HandleInitialData(InitialResourceData initialResourceData, CancellationToken ct)
    {
        logger.LogTrace("Handle initial resource data");

        await connection.DoWithModel(model => model.Resources.Clear());

        foreach (var resource in initialResourceData.Resources)
        {
            ct.ThrowIfCancellationRequested();

            var resourceModel = resource.ToModel();
            var resourceWrapper = new ResourceWrapper();
            resourceWrapper.Model.SetValue(resourceModel);
            await connection.DoWithModel(model => { model.Resources.TryAdd(resourceModel.Name, resourceWrapper); });
        }
    }

    private async Task HandleChanges(WatchResourcesChanges watchResourcesChanges, CancellationToken ct)
    {
        logger.LogTrace("Handle resource changes");

        foreach (var change in watchResourcesChanges.Value)
        {
            ct.ThrowIfCancellationRequested();

            if (change is null) continue;

            switch (change.KindCase)
            {
                case WatchResourcesChange.KindOneofCase.Upsert:
                    await UpsertResource(change);
                    break;
                case WatchResourcesChange.KindOneofCase.Delete:
                    await DeleteResource(change);
                    break;
            }
        }
    }

    private async Task UpsertResource(WatchResourcesChange change)
    {
        var resourceModel = change.Upsert.ToModel();
        await connection.DoWithModel(model =>
        {
            if (model.Resources.ContainsKey(resourceModel.Name))
            {
                model.Resources[resourceModel.Name].Model.SetValue(resourceModel);
            }
            else
            {
                var resourceWrapper = new ResourceWrapper();
                resourceWrapper.Model.SetValue(resourceModel);
                model.Resources.TryAdd(resourceModel.Name, resourceWrapper);
            }
        });
    }

    private async Task DeleteResource(WatchResourcesChange change)
    {
        await connection.DoWithModel(model => model.Resources.Remove(change.Delete.ResourceName));
    }

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}