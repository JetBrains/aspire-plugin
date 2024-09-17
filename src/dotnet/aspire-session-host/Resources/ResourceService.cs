using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Aspire.ResourceService.Proto.V1;
using Grpc.Core;
using JetBrains.Lifetimes;
using JetBrains.Rd.Base;
using JetBrains.Rd.Tasks;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Polly;
using Polly.Registry;
using ResourceCommandRequest = JetBrains.Rider.Aspire.SessionHost.Generated.ResourceCommandRequest;
using ResourceCommandResponse = JetBrains.Rider.Aspire.SessionHost.Generated.ResourceCommandResponse;
using ResourceCommandResponseKind = JetBrains.Rider.Aspire.SessionHost.Generated.ResourceCommandResponseKind;

namespace JetBrains.Rider.Aspire.SessionHost.Resources;

internal sealed class ResourceService(
    Connection connection,
    DashboardService.DashboardServiceClient client,
    ResiliencePipelineProvider<string> resiliencePipelineProvider,
    IOptions<ResourceServiceOptions> options,
    ILogger<ResourceService> logger
) : IDisposable
{
    private const string ApiKeyHeader = "x-resource-service-api-key";
    private readonly Grpc.Core.Metadata _headers = [];
    private readonly ResourceServiceOptions _optionValue = options.Value;
    private readonly LifetimeDefinition _lifetimeDef = new();

    private readonly ResiliencePipeline _pipeline =
        resiliencePipelineProvider.GetPipeline(nameof(ResourceService));

    internal void Initialize()
    {
        if (_optionValue.ApiKey is not null)
        {
            _headers.Add(ApiKeyHeader, _optionValue.ApiKey);
        }

        _lifetimeDef.Lifetime.StartAttachedAsync(TaskScheduler.Default, async () => await WatchResources());
    }

    private async Task WatchResources()
    {
        logger.LogInformation("Start resource watching");

        await Task.Delay(TimeSpan.FromSeconds(5), Lifetime.AsyncLocal.Value);

        var retryCout = 1;
        await _pipeline.ExecuteAsync(
            async token => await SendWatchResourcesRequest(retryCout++, token),
            Lifetime.AsyncLocal.Value
        );

        logger.LogInformation("Stop resource watching, lifetime is alive {isAlive}", Lifetime.AsyncLocal.Value.IsAlive);
    }

    private async Task SendWatchResourcesRequest(
        int retryCount,
        CancellationToken ct)
    {
        var request = new WatchResourcesRequest { IsReconnect = retryCount > 1 };
        var response = client.WatchResources(request, headers: _headers, cancellationToken: ct);
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
            }
        }
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
            resourceWrapper.ExecuteCommand.SetAsync(async (lt, request) => await ExecuteCommand(request, lt));
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
                resourceWrapper.ExecuteCommand.SetAsync(async (lt, request) => await ExecuteCommand(request, lt));
                resourceWrapper.Model.SetValue(resourceModel);
                model.Resources.TryAdd(resourceModel.Name, resourceWrapper);
            }
        });
    }

    private async Task DeleteResource(WatchResourcesChange change)
    {
        await connection.DoWithModel(model => model.Resources.Remove(change.Delete.ResourceName));
    }

    private async Task<ResourceCommandResponse> ExecuteCommand(ResourceCommandRequest command, Lifetime lifetime)
    {
        var request = MapRequest(command);
        var response = await client.ExecuteResourceCommandAsync(request, headers: _headers,
            cancellationToken: lifetime.ToCancellationToken());
        return MapResponse(response);
    }

    private static global::Aspire.ResourceService.Proto.V1.ResourceCommandRequest MapRequest(
        ResourceCommandRequest request) =>
        new()
        {
            CommandType = request.CommandType,
            ResourceName = request.ResourceName,
            ResourceType = request.ResourceType
        };

    private static ResourceCommandResponse MapResponse(
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponse response) =>
        new(
            MapResponseKind(response.Kind),
            response.HasErrorMessage ? response.ErrorMessage : null
        );

    private static ResourceCommandResponseKind MapResponseKind(
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponseKind kind) => kind switch
    {
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponseKind.Undefined => ResourceCommandResponseKind
            .Undefined,
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponseKind.Succeeded => ResourceCommandResponseKind
            .Succeeded,
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponseKind.Failed =>
            ResourceCommandResponseKind.Failed,
        global::Aspire.ResourceService.Proto.V1.ResourceCommandResponseKind.Cancelled => ResourceCommandResponseKind
            .Canceled,
        _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, null)
    };

    public void Dispose()
    {
        _lifetimeDef.Dispose();
    }
}