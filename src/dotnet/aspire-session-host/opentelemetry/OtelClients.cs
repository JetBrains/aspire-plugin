using OpenTelemetry.Proto.Collector.Logs.V1;
using OpenTelemetry.Proto.Collector.Metrics.V1;
using OpenTelemetry.Proto.Collector.Trace.V1;

namespace AspireSessionHost.OpenTelemetry;

internal static class OTelClients
{
    internal static void AddOtelClients(this IServiceCollection services, Uri uri)
    {
        services.AddGrpcClient<LogsService.LogsServiceClient>(o => { o.Address = uri; });
        services.AddGrpcClient<MetricsService.MetricsServiceClient>(o => { o.Address = uri; });
        services.AddGrpcClient<TraceService.TraceServiceClient>(o => { o.Address = uri; });
    }
}