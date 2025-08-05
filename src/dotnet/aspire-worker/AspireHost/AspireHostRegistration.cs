using Polly;
using Polly.Retry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal static class AspireHostRegistration
{
    internal static void AddAspireHostServices(this IServiceCollection services)
    {
        services.AddSingleton<AspireHostService>();

        services.AddResiliencePipeline(nameof(AspireHostResourceWatcher), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant
            });
        });

        services.AddResiliencePipeline(nameof(AspireHostResourceLogWatcher), builder =>
        {
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant
            });
        });
    }

    internal static async Task InitializeAspireHostServices(this IServiceProvider services)
    {
        using var scope = services.CreateScope();
        var sessionEventService = scope.ServiceProvider.GetRequiredService<AspireHostService>();
        await sessionEventService.Initialize();
    }
}