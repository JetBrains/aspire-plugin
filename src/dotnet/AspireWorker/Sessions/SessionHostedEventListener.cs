namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal sealed class SessionHostedEventListener(ISessionService sessionService) : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        await sessionService.SubscribeToSessionEvents();
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        return Task.CompletedTask;
    }
}