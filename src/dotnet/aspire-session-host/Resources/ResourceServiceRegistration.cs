using Aspire.V1;
using Grpc.Core;
using Grpc.Net.Client.Configuration;
using Polly;
using Polly.Retry;
using static AspireSessionHost.EnvironmentVariables;

namespace AspireSessionHost.Resources;

internal static class ResourceServiceRegistration
{
    internal static void AddResourceServices(this IServiceCollection services)
    {
        var resourceEndpointUrl = GetResourceEndpointUrl();
        if (resourceEndpointUrl is null) return;

        services.ConfigureOptions<ResourceServiceOptionSetup>();

        var retryPolicy = new MethodConfig
        {
            Names = { MethodName.Default },
            RetryPolicy = new RetryPolicy
            {
                MaxAttempts = 10,
                InitialBackoff = TimeSpan.FromSeconds(1),
                MaxBackoff = TimeSpan.FromSeconds(5),
                BackoffMultiplier = 1.5,
                RetryableStatusCodes = { StatusCode.Unavailable }
            }
        };
        services
            .AddGrpcClient<DashboardService.DashboardServiceClient>(o => { o.Address = resourceEndpointUrl; })
            .ConfigureChannel(o => { o.ServiceConfig = new ServiceConfig { MethodConfigs = { retryPolicy } }; });
        services.AddSingleton<ResourceService>();
        services.AddSingleton<ResourceLogService>();

        services.AddResiliencePipeline(nameof(ResourceLogService), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant,
                ShouldHandle = new PredicateBuilder().HandleResult(result => result is bool boolResult && !boolResult)
            });
        });

        services.AddResiliencePipeline(nameof(ResourceService), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant
            });
        });
    }

    internal static async Task InitializeResourceServices(this IServiceProvider services)
    {
        using var scope = services.CreateScope();
        var resourceService = scope.ServiceProvider.GetRequiredService<ResourceService>();
        resourceService.Initialize();
        var resourceLogService = scope.ServiceProvider.GetRequiredService<ResourceLogService>();
        await resourceLogService.Initialize();
    }
}