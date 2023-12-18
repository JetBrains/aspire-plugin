using Grpc.Core;
using OpenTelemetry.Proto.Collector.Logs.V1;

namespace AspireSessionHost.Otel;

internal sealed class OtelLogService : LogsService.LogsServiceBase
{
    public override Task<ExportLogsServiceResponse> Export(
        ExportLogsServiceRequest request,
        ServerCallContext context)
    {
        return Task.FromResult(new ExportLogsServiceResponse
        {
            PartialSuccess = new ExportLogsPartialSuccess
            {
                RejectedLogRecords = 0
            }
        });
    }
}