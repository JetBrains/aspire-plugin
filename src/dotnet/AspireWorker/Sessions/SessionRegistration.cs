namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal static class SessionRegistration
{
    internal static void AddAspireSessionServices(this IServiceCollection services)
    {
        services.AddHostedService<SessionHostedEventListener>();
        services.AddSingleton<ISessionService, SessionService>();
    }
}