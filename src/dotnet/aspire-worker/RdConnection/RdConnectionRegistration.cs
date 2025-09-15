namespace JetBrains.Rider.Aspire.Worker.RdConnection;

internal static class RdConnectionRegistration
{
    internal static void AddRdConnectionServices(this IServiceCollection services, ConfigurationManager configuration)
    {
        var connection = new Connection(configuration);
        services.AddSingleton(connection);

        services.AddHostedService<AspireHostListener>();
    }
}