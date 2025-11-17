namespace JetBrains.Rider.Aspire.Worker.RdConnection;

internal static class RdConnectionRegistration
{
    internal static void AddRdConnectionServices(this IServiceCollection services)
    {
        services.AddHostedService<RdConnectionHostedService>();
        services.AddSingleton<RdConnection>();
        services.AddSingleton<IRdConnectionWrapper, RdConnectionWrapper>();
    }
}