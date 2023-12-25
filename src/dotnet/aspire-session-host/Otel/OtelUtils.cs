using OpenTelemetry.Proto.Common.V1;
using OpenTelemetry.Proto.Resource.V1;

namespace AspireSessionHost.Otel;

internal static class OtelUtils
{
    private const string ServiceName = "service.name";

    internal static string? GetServiceName(this Resource resource)
    {
        foreach (var attribute in resource.Attributes)
        {
            if (attribute.Key == ServiceName && attribute.Value.ValueCase is AnyValue.ValueOneofCase.StringValue)
            {
                return attribute.Value.StringValue;
            }
        }

        return null;
    }
}