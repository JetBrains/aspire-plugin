using AspireSessionHost.Sessions;
using OpenTelemetry.Proto.Collector.Logs.V1;
using OpenTelemetry.Proto.Collector.Metrics.V1;
using OpenTelemetry.Proto.Collector.Trace.V1;
using static AspireSessionHost.EnvironmentVariables;

namespace AspireSessionHost.OTel;

internal static class OTelServiceRegistration
{
    internal static void AddOTelServices(this IServiceCollection services)
    {
        var otlpEndpointUrl = GetOtlpEndpointUrl();
        if (otlpEndpointUrl == null) return;

        services.AddGrpcClient<LogsService.LogsServiceClient>(o => { o.Address = otlpEndpointUrl; });
        services.AddGrpcClient<MetricsService.MetricsServiceClient>(o => { o.Address = otlpEndpointUrl; });
        services.AddGrpcClient<TraceService.TraceServiceClient>(o => { o.Address = otlpEndpointUrl; });

        services.AddSingleton<ResourceMetricService>();
        services.AddSingleton<SessionNodeService>();
    }

    internal static async Task InitializeOTelServices(this IServiceProvider services)
    {
        using var scope = services.CreateScope();

        var sessionMetricService = scope.ServiceProvider.GetRequiredService<ResourceMetricService>();
        await sessionMetricService.Initialize();

        var sessionNodeService = scope.ServiceProvider.GetRequiredService<SessionNodeService>();
        await sessionNodeService.Initialize();
    }
}