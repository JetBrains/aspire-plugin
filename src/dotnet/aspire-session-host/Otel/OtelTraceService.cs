using Grpc.Core;
using OpenTelemetry.Proto.Collector.Trace.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelTraceService(TraceService.TraceServiceClient client) : TraceService.TraceServiceBase
{
    public override async Task<ExportTraceServiceResponse> Export(
        ExportTraceServiceRequest request,
        ServerCallContext context)
    {
        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }
}