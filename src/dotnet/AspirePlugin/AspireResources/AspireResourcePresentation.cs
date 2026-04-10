using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Application.UI.Icons.CommonThemedIcons;
using JetBrains.UI.Icons;

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
        return $"Aspire resource '{resourceName}' has state '{GetStateText(resource)}'";
    }

    private static string GetStateText(AspireRdResource resource) =>
        resource.State switch
        {
            AspireRdResourceState.Running when resource.HealthStatus is { } healthStatus => $"Running ({healthStatus})",
            AspireRdResourceState.Building => "Building",
            AspireRdResourceState.Starting => "Starting",
            AspireRdResourceState.Running => "Running",
            AspireRdResourceState.FailedToStart => "Failed to start",
            AspireRdResourceState.RuntimeUnhealthy => "Runtime unhealthy",
            AspireRdResourceState.Stopping => "Stopping",
            AspireRdResourceState.Exited => "Exited",
            AspireRdResourceState.Finished => "Finished",
            AspireRdResourceState.Waiting => "Waiting",
            AspireRdResourceState.NotStarted => "Not started",
            AspireRdResourceState.Hidden => "Hidden",
            _ => "Unknown"
        };

    private static bool ShouldAppendExitCode(AspireRdResource resource)
    {
        return resource.State is AspireRdResourceState.Exited
            or AspireRdResourceState.NotStarted
            or AspireRdResourceState.FailedToStart;
    }

    public static IconId GetIconId(AspireRdResource resource)
    {
        var badge = GetHealthStatusBadge(resource);
        return badge switch
        {
            ResourceIconBadge.None => CommonThemedIcons.Info.Id,
            ResourceIconBadge.Live => CommonThemedIcons.Success.Id,
            ResourceIconBadge.Warning => CommonThemedIcons.Warning.Id,
            ResourceIconBadge.Error => CommonThemedIcons.Error.Id,
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    // ReSharper disable once CognitiveComplexity
    // ReSharper disable once CyclomaticComplexity
    private static ResourceIconBadge GetHealthStatusBadge(AspireRdResource resource)
    {
        var state = resource.State;

        if (state is null) return ResourceIconBadge.None;

        if (state is AspireRdResourceState.Exited
            or AspireRdResourceState.Finished
            or AspireRdResourceState.FailedToStart)
        {
            if (resource.ExitCode.HasValue && resource.ExitCode.Value != 0)
                return ResourceIconBadge.Error;
            if (state is AspireRdResourceState.FailedToStart)
                return ResourceIconBadge.Warning;

            return ResourceIconBadge.None;
        }

        if (state is AspireRdResourceState.Starting
            or AspireRdResourceState.Building
            or AspireRdResourceState.Waiting
            or AspireRdResourceState.Stopping
            or AspireRdResourceState.NotStarted
            or AspireRdResourceState.Unknown)
        {
            return ResourceIconBadge.None;
        }

        if (state is AspireRdResourceState.RuntimeUnhealthy)
            return ResourceIconBadge.Warning;

        if (resource.StateStyle != null && resource.StateStyle is not AspireRdResourceStateStyle.Unknown)
        {
            return resource.StateStyle switch
            {
                AspireRdResourceStateStyle.Success => ResourceIconBadge.Live,
                AspireRdResourceStateStyle.Warning => ResourceIconBadge.Warning,
                AspireRdResourceStateStyle.Error => ResourceIconBadge.Error,
                _ => ResourceIconBadge.None
            };
        }

        if (resource.HealthStatus is AspireRdResourceHealthStatus.Unhealthy or AspireRdResourceHealthStatus.Degraded)
            return ResourceIconBadge.Warning;

        return state is AspireRdResourceState.Running ? ResourceIconBadge.Live : ResourceIconBadge.None;
    }

    private enum ResourceIconBadge
    {
        None,
        Live,
        Warning,
        Error
    }
}