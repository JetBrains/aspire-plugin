namespace AspireSessionHost;

internal static class EnvironmentVariables
{
    //This variable is used to configure the endpoint to interact with DCP to create, delete session, etc.
    internal const string AspNetCoreUrls = "ASPNETCORE_URLS";
    internal static Uri? GetAspNetCoreUrls() => GetUrlFromEnvironment(AspNetCoreUrls);

    private static Uri? GetUrlFromEnvironment(string variableName)
    {
        var variable = Environment.GetEnvironmentVariable(variableName);
        if (string.IsNullOrEmpty(variable)) return null;
        Uri.TryCreate(variable, UriKind.Absolute, out var url);
        return url;
    }
}