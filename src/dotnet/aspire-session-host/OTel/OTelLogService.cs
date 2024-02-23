using Grpc.Core;

namespace AspireSessionHost.OTel;

internal sealed class OTelLogService(LogsService.LogsServiceClient client) : LogsService.LogsServiceBase
{
    public override async Task<ExportLogsServiceResponse> Export(
        ExportLogsServiceRequest request,
        ServerCallContext context)
    {
        return await client.ExportAsync(request, context.RequestHeaders, context.Deadline, context.CancellationToken);
    }
}
