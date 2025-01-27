using Polly;
using Polly.Retry;

namespace JetBrains.Rider.Aspire.SessionHost.AspireHost;

internal static class AspireHostRegistration
{
    internal static void AddAspireHostServices(this IServiceCollection services)
    {
        services.AddSingleton<AspireHostService>();

        services.AddResiliencePipeline(nameof(AspireHostResourceWatcher), builder =>
        {
            //TODO: Ignore cancellation
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant
            });
        });

        services.AddResiliencePipeline(nameof(AspireHostResourceLogWatcher), builder =>
        {
            //TODO: Ignore cancellation
            builder.AddRetry(new RetryStrategyOptions
            {
                MaxRetryAttempts = 10,
                Delay = TimeSpan.FromSeconds(2),
                BackoffType = DelayBackoffType.Constant,
                ShouldHandle = new PredicateBuilder().HandleResult(result => result is bool boolResult && !boolResult)
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