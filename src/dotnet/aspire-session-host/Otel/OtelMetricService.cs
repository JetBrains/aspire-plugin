using Grpc.Core;
using OpenTelemetry.Proto.Collector.Metrics.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelMetricService(MetricsService.MetricsServiceClient client) : MetricsService.MetricsServiceBase
{
    public override async Task<ExportMetricsServiceResponse> Export(
        ExportMetricsServiceRequest request,
        ServerCallContext context)
    {
        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }
}