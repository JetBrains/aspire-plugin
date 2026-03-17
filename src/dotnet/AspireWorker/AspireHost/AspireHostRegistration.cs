namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal static class AspireHostRegistration
{
    internal static void AddAspireHostServices(this IServiceCollection services)
    {
        services.AddHostedService<AspireHostListener>();

        services.AddSingleton<IAspireHostService, AspireHostService>();
    }
}