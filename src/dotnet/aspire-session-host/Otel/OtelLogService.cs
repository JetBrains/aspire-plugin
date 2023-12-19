using Grpc.Core;
using OpenTelemetry.Proto.Collector.Logs.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelLogService(LogsService.LogsServiceClient client) : LogsService.LogsServiceBase
{
    public override async Task<ExportLogsServiceResponse> Export(
        ExportLogsServiceRequest request,
        ServerCallContext context)
    {
        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }
}