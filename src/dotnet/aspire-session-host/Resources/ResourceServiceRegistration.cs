using Aspire.ResourceService.Proto.V1;
using Grpc.Core;
using Grpc.Net.Client.Configuration;
using Polly;
using Polly.Retry;

namespace AspireSessionHost.Resources;

internal static class ResourceServiceRegistration
{
    internal static void AddResourceServices(this IServiceCollection services, ConfigurationManager configuration)
    {
        var resourceServiceOptions = configuration
            .GetSection(ConfigureResourceServiceOptions.SectionName)
            .Get<ResourceServiceOptions>();
        if (resourceServiceOptions?.EndpointUrl is null) return;

        if (!Uri.TryCreate(resourceServiceOptions.EndpointUrl, UriKind.Absolute, out var resourceEndpointUrl)) return;

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

    internal static async Task InitializeResourceServices(this IServiceProvider services, IConfiguration configuration)
    {
        var resourceServiceOptions = configuration
            .GetSection(ConfigureResourceServiceOptions.SectionName)
            .Get<ResourceServiceOptions>();
        if (resourceServiceOptions?.EndpointUrl is null) return;

        using var scope = services.CreateScope();
        var resourceService = scope.ServiceProvider.GetRequiredService<ResourceService>();
        resourceService.Initialize();
        var resourceLogService = scope.ServiceProvider.GetRequiredService<ResourceLogService>();
        await resourceLogService.Initialize();
    }
}