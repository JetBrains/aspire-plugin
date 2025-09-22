using Polly;
using Polly.Retry;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal static class AspireHostRegistration
{
    internal static void AddAspireHostServices(this IServiceCollection services)
    {
        services.AddHostedService<AspireHostListener>();

        services.AddSingleton<IAspireHostService, AspireHostService>();

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
}