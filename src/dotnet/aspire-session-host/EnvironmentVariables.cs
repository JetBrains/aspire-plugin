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

    //This variable is used to configure OpenTelemetry Protocol gRPC endpoint to receive telemetry data from the child
    //projects.
    private const string OtlpServerPort = "RIDER_OTLP_SERVER_PORT";
    internal static int? GetOtlpServerPort() => GetPortFromEnvironment(OtlpServerPort);

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