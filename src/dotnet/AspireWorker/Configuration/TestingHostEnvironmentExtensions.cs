namespace JetBrains.Rider.Aspire.Worker.Configuration;

internal static class TestingHostEnvironmentExtensions
{
    internal const string Testing = "Testing";

    internal static bool IsTesting(this IHostEnvironment hostEnvironment) =>
        hostEnvironment.IsEnvironment(Testing);
}