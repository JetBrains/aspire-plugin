using Aspire.V1;
using Grpc.Core;
using Grpc.Net.Client.Configuration;
using Polly;
using Polly.Retry;

namespace AspireSessionHost.Resources;

internal static class ResourceServiceRegistration
{
    internal static void AddResourceServices(this IServiceCollection services)
    {
        var resourceEndpointUrlValue = Environment.GetEnvironmentVariable("RIDER_RESOURCE_ENDPOINT_URL");
        if (string.IsNullOrEmpty(resourceEndpointUrlValue)) return;

        Uri.TryCreate(resourceEndpointUrlValue, UriKind.Absolute, out var resourceEndpointUrl);
        if (resourceEndpointUrl is null) return;

        var retryPolicy = new MethodConfig
        {
            Names = { MethodName.Default },
            RetryPolicy = new RetryPolicy
            {
                MaxAttempts = 5,
                InitialBackoff = TimeSpan.FromSeconds(1),
                MaxBackoff = TimeSpan.FromSeconds(5),
                BackoffMultiplier = 1.5,
                RetryableStatusCodes = { StatusCode.Unavailable }
            }
        };
        services
            .AddGrpcClient<DashboardService.DashboardServiceClient>(o => { o.Address = resourceEndpointUrl; })
            .ConfigureChannel(o => { o.ServiceConfig = new ServiceConfig { MethodConfigs = { retryPolicy } }; });
        services.AddSingleton<SessionResourceService>();
        services.AddSingleton<SessionResourceLogService>();

        services.AddResiliencePipeline(nameof(SessionResourceLogService), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 5,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant,
                ShouldHandle = new PredicateBuilder().HandleResult(result => result is bool boolResult && !boolResult)
            });
        });

        services.AddResiliencePipeline(nameof(SessionResourceService), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 5,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant
            });
        });
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