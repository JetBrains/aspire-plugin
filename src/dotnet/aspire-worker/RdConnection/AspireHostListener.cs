using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.AspireHost;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

internal sealed class AspireHostListener(Connection connection, AspireHostService hostService) : IHostedService
{
    private readonly LifetimeDefinition _lifetimeDef = new();

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        await connection.DoWithModel(model =>
        {
            model.AspireHosts.View(_lifetimeDef.Lifetime, (lifetime, id, host) =>
            {
                hostService.AddNewHost(id, host, lifetime);
            });
        });
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _lifetimeDef.Dispose();
        return Task.CompletedTask;
    }
}