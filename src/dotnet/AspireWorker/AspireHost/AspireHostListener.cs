using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.RdConnection;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class AspireHostListener(IRdConnectionWrapper connectionWrapper, IAspireHostService hostService)
    : IHostedService
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        await connectionWrapper.ViewHosts(_lifetimeDef.Lifetime,
            (lifetime, id, host) => { hostService.AddNewHost(id, host, lifetime); });
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _lifetimeDef.Dispose();
        return Task.CompletedTask;
    }
}