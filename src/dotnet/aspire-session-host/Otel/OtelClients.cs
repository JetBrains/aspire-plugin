using OpenTelemetry.Proto.Collector.Logs.V1;
using OpenTelemetry.Proto.Collector.Metrics.V1;
using OpenTelemetry.Proto.Collector.Trace.V1;

namespace AspireSessionHost.Otel;

internal static class OtelClients
{
    internal static void AddOtelClients(this IServiceCollection services, Uri uri)
    {
        services.AddGrpcClient<LogsService.LogsServiceClient>(o => { o.Address = uri; });
        services.AddGrpcClient<MetricsService.MetricsServiceClient>(o => { o.Address = uri; });
        services.AddGrpcClient<TraceService.TraceServiceClient>(o => { o.Address = uri; });
    }
}