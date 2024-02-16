namespace AspireSessionHost.OpenTelemetry;

internal static class OTelEndpoints
{
    internal static void MapOTelEndpoints(this IEndpointRouteBuilder routes)
    {
        routes.MapGrpcService<OTelLogService>();
        routes.MapGrpcService<OTelMetricService>();
        routes.MapGrpcService<OTelTraceService>();
    }
}
