using Grpc.Core;
using OpenTelemetry.Proto.Collector.Metrics.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelMetricService : MetricsService.MetricsServiceBase
{
    public override Task<ExportMetricsServiceResponse> Export(
        ExportMetricsServiceRequest request,
        ServerCallContext context)
    {
        return Task.FromResult(new ExportMetricsServiceResponse
        {
            PartialSuccess = new ExportMetricsPartialSuccess
            {
                RejectedDataPoints = 0
            }
        });
    }
}