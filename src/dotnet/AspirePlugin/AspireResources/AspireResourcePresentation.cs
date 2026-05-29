using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Application.UI.Icons.CommonThemedIcons;
using JetBrains.Rider.Aspire.Plugin.Resources;
using JetBrains.UI.Icons;

namespace JetBrains.Rider.Aspire.Plugin.AspireResources;

internal static class AspireResourcePresentation
{
    public static string GetDisplayText(AspireRdResource resource)
    {
        var stateText = GetStateText(resource);
        if (ShouldAppendExitCode(resource) && resource.ExitCode is { } exitCode)
        {
            return $"{stateText} ({Strings.ExitCode} {exitCode})";
        }

        return stateText;
    }

    public static string GetDescriptionText(AspireRdResource resource)
    {
        var resourceName = string.IsNullOrEmpty(resource.DisplayName) ? resource.Name : resource.DisplayName;
        return $"{Strings.AspireResource} '{resourceName}'";
    }

    private static string GetStateText(AspireRdResource resource) =>
        resource.State switch
        {
            AspireRdResourceState.Running when resource.HealthStatus is { } healthStatus => $"{Strings.Running} ({GetHealthText(healthStatus)})",
            AspireRdResourceState.Building => Strings.Building,
            AspireRdResourceState.Starting => Strings.Starting,
            AspireRdResourceState.Running => Strings.Running,
            AspireRdResourceState.FailedToStart => Strings.FailedToStart,
            AspireRdResourceState.RuntimeUnhealthy => Strings.RuntimeUnhealthy,
            AspireRdResourceState.Stopping => Strings.Stopping,
            AspireRdResourceState.Exited => Strings.Exited,
            AspireRdResourceState.Finished => Strings.Finished,
            AspireRdResourceState.Waiting => Strings.Waiting,
            AspireRdResourceState.NotStarted => Strings.NotStarted,
            AspireRdResourceState.Hidden => Strings.Hidden,
            _ => Strings.Unknown
        };

    private static string GetHealthText(AspireRdResourceHealthStatus status) =>
        status switch
        {
            AspireRdResourceHealthStatus.Healthy => Strings.Healthy,
            AspireRdResourceHealthStatus.Unhealthy => Strings.Unhealthy,
            AspireRdResourceHealthStatus.Degraded => Strings.Degraded,
            _ => throw new ArgumentOutOfRangeException(nameof(status), status, null)
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