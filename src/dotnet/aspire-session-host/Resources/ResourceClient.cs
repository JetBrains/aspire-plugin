using Aspire.V1;

namespace AspireSessionHost.Resources;

internal static class ResourceClient
{
    internal static void AddResourceClient(this IServiceCollection services, Uri uri)
    {
        services.AddGrpcClient<DashboardService.DashboardServiceClient>(o => { o.Address = uri; });
    }
}