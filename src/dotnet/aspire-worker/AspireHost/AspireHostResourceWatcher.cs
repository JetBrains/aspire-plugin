using Aspire.DashboardService.Proto.V1;
using Grpc.Core;
using JetBrains.Lifetimes;
using JetBrains.Rd.Base;
using JetBrains.Rd.Tasks;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using Polly;
using Polly.Registry;
using ResourceCommandRequest = JetBrains.Rider.Aspire.Worker.Generated.ResourceCommandRequest;
using ResourceCommandResponse = JetBrains.Rider.Aspire.Worker.Generated.ResourceCommandResponse;
using ResourceCommandResponseKind = JetBrains.Rider.Aspire.Worker.Generated.ResourceCommandResponseKind;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostResourceWatcher(
    DashboardService.DashboardServiceClient client,
    Metadata headers,
    RdConnection.RdConnection connection,
    AspireHostModel hostModel,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    ILogger logger,
    Lifetime lifetime)
{
    private readonly ResiliencePipeline _pipeline =
        resiliencePipelineProvider.GetPipeline(nameof(AspireHostResourceWatcher));

    internal async Task WatchResources()
    {
        try
        {
            logger.StartResourceWatching();

            await Task.Delay(TimeSpan.FromSeconds(5), lifetime);

            var retryCout = 1;
            await _pipeline.ExecuteAsync(async token => await SendWatchResourcesRequest(retryCout++, token), lifetime);

            logger.StopResourceWatching(lifetime.IsAlive);
        }
        catch (OperationCanceledException)
        {
            logger.ResourceWatchingWasCancelled();
        }
    }

    private async Task SendWatchResourcesRequest(int retryCount, CancellationToken ct)
    {
        try
        {
            var request = new WatchResourcesRequest { IsReconnect = retryCount > 1 };
            var response = client.WatchResources(request, headers: headers, cancellationToken: ct);
            await foreach (var update in response.ResponseStream.ReadAllAsync(ct))
            {
                switch (update.KindCase)
                {
                    case WatchResourcesUpdate.KindOneofCase.InitialData:
                        await HandleInitialData(update.InitialData, ct);
                        break;
                    case WatchResourcesUpdate.KindOneofCase.Changes:
                        await HandleChanges(update.Changes, ct);
                        break;
                    case WatchResourcesUpdate.KindOneofCase.None:
                        break;
                    default:
                        throw new ArgumentOutOfRangeException(update.KindCase.ToString());
                }
            }
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            logger.ResourceWatchingRequestWasCancelled();
        }
    }

    private async Task HandleInitialData(InitialResourceData initialResourceData, CancellationToken ct)
    {
        logger.HandleInitialResourceData();

        await connection.DoWithModel(_ => hostModel.Resources.Clear());

        foreach (var resource in initialResourceData.Resources)
        {
            ct.ThrowIfCancellationRequested();

            var resourceModel = resource.ToModel();
            var resourceWrapper = new ResourceWrapper();
            resourceWrapper.ExecuteCommand.SetAsync(async (lt, request) => await ExecuteCommand(request, lt));
            resourceWrapper.Model.SetValue(resourceModel);
            await connection.DoWithModel(_ => { hostModel.Resources.TryAdd(resourceModel.Name, resourceWrapper); });
        }
    }

    private async Task HandleChanges(WatchResourcesChanges watchResourcesChanges, CancellationToken ct)
    {
        logger.HandleResourceChanges();

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
                case WatchResourcesChange.KindOneofCase.None:
                    break;
                default:
                    throw new ArgumentOutOfRangeException(change.KindCase.ToString());
            }
        }
    }

    private async Task UpsertResource(WatchResourcesChange change)
    {
        var resourceModel = change.Upsert.ToModel();
        await connection.DoWithModel(_ =>
        {
            if (hostModel.Resources.ContainsKey(resourceModel.Name))
            {
                hostModel.Resources[resourceModel.Name].Model.SetValue(resourceModel);
            }
            else
            {
                var resourceWrapper = new ResourceWrapper();
                resourceWrapper.ExecuteCommand.SetAsync(async (lt, request) => await ExecuteCommand(request, lt));
                resourceWrapper.Model.SetValue(resourceModel);
                hostModel.Resources.TryAdd(resourceModel.Name, resourceWrapper);
            }
        });
    }

    private async Task DeleteResource(WatchResourcesChange change)
    {
        await connection.DoWithModel(_ => hostModel.Resources.Remove(change.Delete.ResourceName));
    }

    private async Task<ResourceCommandResponse> ExecuteCommand(ResourceCommandRequest command, Lifetime lt)
    {
        var request = MapRequest(command);
        var response = await client.ExecuteResourceCommandAsync(request, headers: headers,
            cancellationToken: lt.ToCancellationToken());
        return MapResponse(response);
    }

    private static global::Aspire.DashboardService.Proto.V1.ResourceCommandRequest MapRequest(
        ResourceCommandRequest request) =>
        new()
        {
            CommandName = request.CommandName,
            ResourceName = request.ResourceName,
            ResourceType = request.ResourceType
        };

    private static ResourceCommandResponse MapResponse(
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponse response) =>
        new(
            MapResponseKind(response.Kind),
            response.HasErrorMessage ? response.ErrorMessage : null
        );

    private static ResourceCommandResponseKind MapResponseKind(
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponseKind kind) => kind switch
    {
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponseKind.Undefined =>
            ResourceCommandResponseKind.Undefined,
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponseKind.Succeeded =>
            ResourceCommandResponseKind.Succeeded,
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponseKind.Failed =>
            ResourceCommandResponseKind.Failed,
        global::Aspire.DashboardService.Proto.V1.ResourceCommandResponseKind.Cancelled =>
            ResourceCommandResponseKind.Canceled,
        _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, null)
    };
}