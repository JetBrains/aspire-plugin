using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rd;
using JetBrains.Rd.Impl;
using JetBrains.Rider.Aspire.Worker.Configuration;
using JetBrains.Rider.Aspire.Worker.Generated;
using Microsoft.Extensions.Options;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

/// <summary>
/// A hosted service responsible for managing the lifetime and initialization of the RD protocol connection.
/// </summary>
/// <remarks>
/// This service establishes a connection between the Aspire Worker application and the Rider IDE using the RD protocol.
/// </remarks>
/// <remarks>
/// The service reads the RD port configuration from environment options passed via <see cref="ConnectionOptions"/>.
/// If the RD port is not configured, the service logs a warning and skips the connection setup.
/// </remarks>
/// <remarks>
/// Upon successful initialization, the connection model and corresponding scheduler are passed to the provided
/// <see cref="RdConnection"/> instance for subsequent operations.
/// </remarks>
internal sealed class RdConnectionHostedService(
    IOptions<ConnectionOptions> connectionOptions,
    RdConnection rdConnection,
    ILogger<RdConnectionHostedService> logger)
    : IHostedService
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        var rdPort = connectionOptions.Value.RdPort;
        if (!rdPort.HasValue)
        {
            logger.LogWarning("Unable to find RD port environment variable. Skip connecting to Rider.");
            return;
        }

        var scheduler = SingleThreadScheduler.RunOnSeparateThread(
            _lifetimeDef.Lifetime,
            "Aspire Worker Protocol Connection"
        );
        var wire = new SocketWire.Client(
            _lifetimeDef.Lifetime,
            scheduler,
            rdPort.Value
        );
        var protocol = new Protocol(
            "Aspire Worker Protocol",
            new Serializers(),
            new Identities(IdKind.Client),
            scheduler,
            wire,
            _lifetimeDef.Lifetime
        );

        var tcs = new TaskCompletionSource<AspireWorkerModel>(TaskCreationOptions.RunContinuationsAsynchronously);
        scheduler.Queue(() =>
        {
            try
            {
                tcs.SetResult(new AspireWorkerModel(_lifetimeDef.Lifetime, protocol));
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        });

        var model = await tcs.Task;
        rdConnection.InitializeWithModelAndScheduler(model, scheduler);
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _lifetimeDef.Dispose();
        return Task.CompletedTask;
    }
}