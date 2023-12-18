using Grpc.Core;
using OpenTelemetry.Proto.Collector.Trace.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelTraceService : TraceService.TraceServiceBase
{
    public override Task<ExportTraceServiceResponse> Export(
        ExportTraceServiceRequest request,
        ServerCallContext context)
    {
        return Task.FromResult(new ExportTraceServiceResponse
        {
            PartialSuccess = new ExportTracePartialSuccess
            {
                RejectedSpans = 0
            }
        });
    }
}