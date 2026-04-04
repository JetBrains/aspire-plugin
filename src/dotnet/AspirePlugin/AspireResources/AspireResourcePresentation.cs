using JetBrains.Rider.Aspire.Plugin.Generated;

namespace JetBrains.Rider.Aspire.Plugin.AspireResources;

internal static class AspireResourcePresentation
{
    public static string GetDisplayText(AspireRdResource resource)
    {
        var stateText = GetStateText(resource);
        if (ShouldAppendExitCode(resource) && resource.ExitCode is { } exitCode)
        {
            return $"{stateText} (exit code {exitCode})";
        }

        return stateText;
    }

    public static string GetDescriptionText(AspireRdResource resource)
    {
        var resourceName = string.IsNullOrEmpty(resource.DisplayName) ? resource.Name : resource.DisplayName;
        return $"Aspire resource '{resourceName}' is {GetDisplayText(resource)}";
    }

    private static string GetStateText(AspireRdResource resource)
    {
        return resource.State switch
        {
            AspireRdResourceState.Running when resource.HealthStatus is { } healthStatus => $"Running ({healthStatus})",
            AspireRdResourceState.Running => "Running",
            AspireRdResourceState.Starting => "Starting",
            AspireRdResourceState.Waiting => "Waiting",
            AspireRdResourceState.Stopping => "Stopping",
            AspireRdResourceState.RuntimeUnhealthy => "Runtime unhealthy",
            AspireRdResourceState.FailedToStart => "Failed to start",
            AspireRdResourceState.Exited => "Stopped",
            AspireRdResourceState.NotStarted => "Stopped",
            AspireRdResourceState.Finished => "Completed",
            _ => "Unknown"
        };
    }

    private static bool ShouldAppendExitCode(AspireRdResource resource)
    {
        return resource.State is AspireRdResourceState.Exited
            or AspireRdResourceState.NotStarted
            or AspireRdResourceState.FailedToStart;
    }
}
