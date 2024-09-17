using System;
using System.Threading.Tasks;
using Microsoft.Extensions.DependencyInjection;

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

internal static class SessionServiceRegistration
{
    internal static void AddSessionServices(this IServiceCollection services)
    {
        services.AddSingleton<SessionEventService>();
        services.AddSingleton<SessionService>();
    }

    internal static async Task InitializeSessionServices(this IServiceProvider services)
    {
        using var scope = services.CreateScope();
        var sessionEventService = scope.ServiceProvider.GetRequiredService<SessionEventService>();
        await sessionEventService.Initialize();
    }
}