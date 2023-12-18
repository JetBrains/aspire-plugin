namespace AspireSessionHost.Otel;

internal static class OtelEndpoints
{
    internal static void MapOtelEndpoints(this IEndpointRouteBuilder routes)
    {
        routes.MapGrpcService<OtelLogService>();
        routes.MapGrpcService<OtelMetricService>();
        routes.MapGrpcService<OtelTraceService>();
    }
}