using Microsoft.Extensions.Options;

namespace AspireSessionHost.OTel;

internal static class OTelEndpoints
{
    internal static void MapOTelEndpoints(this WebApplication app)
    {
        using (var scope = app.Services.CreateScope())
        {
            var options = scope.ServiceProvider.GetRequiredService<IOptions<OTelServiceOptions>>().Value;
            if (options.EndpointUrl is null) return;
        }

        app.MapGrpcService<OTelLogService>();
        app.MapGrpcService<OTelMetricService>();
        app.MapGrpcService<OTelTraceService>();
    }
}
