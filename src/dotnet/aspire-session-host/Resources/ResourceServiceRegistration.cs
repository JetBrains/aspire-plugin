using Aspire.V1;

namespace AspireSessionHost.Resources;

internal static class ResourceServiceRegistration
{
    internal static void AddResourceServices(this IServiceCollection services)
    {
        var resourceEndpointUrlValue = Environment.GetEnvironmentVariable("RIDER_RESOURCE_ENDPOINT_URL");
        if (string.IsNullOrEmpty(resourceEndpointUrlValue)) return;

        Uri.TryCreate(resourceEndpointUrlValue, UriKind.Absolute, out var resourceEndpointUrl);
        if (resourceEndpointUrl is null) return;

        services.AddGrpcClient<DashboardService.DashboardServiceClient>(o => { o.Address = resourceEndpointUrl; });
        services.AddSingleton<SessionResourceService>();
        services.AddSingleton<SessionResourceLogService>();
    }

    internal static async Task InitializeResourceServices(this IServiceProvider services)
    {
        using var scope = services.CreateScope();
        var resourceService = scope.ServiceProvider.GetRequiredService<SessionResourceService>();
        resourceService.Initialize();
        var resourceLogService = scope.ServiceProvider.GetRequiredService<SessionResourceLogService>();
        await resourceLogService.Initialize();
    }
}