using System.Globalization;

namespace AspireSessionHost;

internal static class EnvironmentVariables
{
    //This variable is used to configure the endpoint to interact with DCP to create, delete session, etc.
    internal const string AspNetCoreUrls = "ASPNETCORE_URLS";
    internal static Uri? GetAspNetCoreUrls() => GetUrlFromEnvironment(AspNetCoreUrls);

    //This variable is used to configure RD protocol to interact with the IDE.
    internal const string RdPort = "RIDER_RD_PORT";
    internal static int? GetRdPort() => GetPortFromEnvironment(RdPort);

    //This variable is used to configure the Resource service gRPC client to retrieve resources and display them
    //on the IDE dashboard (originally DOTNET_RESOURCE_SERVICE_ENDPOINT_URL).
    private const string ResourceEndpointUrl = "RIDER_RESOURCE_ENDPOINT_URL";
    internal static Uri? GetResourceEndpointUrl() => GetUrlFromEnvironment(ResourceEndpointUrl);

    //This variable is used to configure OpenTelemetry Protocol gRPC endpoint to receive telemetry data from the child
    //projects.
    private const string OtlpServerPort = "RIDER_OTLP_SERVER_PORT";
    internal static int? GetOtlpServerPort() => GetPortFromEnvironment(OtlpServerPort);

    //This variable is used to configure OpenTelemetry Protocol gRPC client to send telemetry data from the child
    //projects to the Dashboard service after it has been processed (originally DOTNET_DASHBOARD_OTLP_ENDPOINT_URL).
    private const string OtlpEndpointUrl = "RIDER_OTLP_ENDPOINT_URL";
    internal static Uri? GetOtlpEndpointUrl() => GetUrlFromEnvironment(OtlpEndpointUrl);

    private static Uri? GetUrlFromEnvironment(string variableName)
    {
        var variable = Environment.GetEnvironmentVariable(variableName);
        if (string.IsNullOrEmpty(variable)) return null;
        Uri.TryCreate(variable, UriKind.Absolute, out var url);
        return url;
    }

    private static int? GetPortFromEnvironment(string variableName)
    {
        var variable = Environment.GetEnvironmentVariable(variableName);
        if (string.IsNullOrEmpty(variable)) return null;
        if (!int.TryParse(variable, CultureInfo.InvariantCulture, out var port)) return null;
        return port;
    }
}