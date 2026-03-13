using Aspire.DashboardService.Proto.V1;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal static partial class Log
{
    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Resource watching is enabled for the host {aspireHostId}.")]
    internal static partial void ResourceWatchingIsEnabled(this ILogger logger, string aspireHostId);

    [LoggerMessage(
        Level = LogLevel.Debug,
        Message = "Creating resource log watcher for {aspireHostId}.")]
    internal static partial void CreatingResourceLogWatcher(this ILogger logger, string aspireHostId);

    [LoggerMessage(
        Level = LogLevel.Debug,
        Message = "Creating resource watcher for {aspireHostId}.")]
    internal static partial void CreatingResourceWatcher(this ILogger logger, string aspireHostId);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Start resource watching.")]
    internal static partial void StartResourceWatching(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Stop resource watching, lifetime is alive {isAlive}.")]
    internal static partial void StopResourceWatching(this ILogger logger, bool isAlive);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Resource watching was cancelled.")]
    internal static partial void ResourceWatchingWasCancelled(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Resource watching request was cancelled.")]
    internal static partial void ResourceWatchingRequestWasCancelled(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Handle initial resource data.")]
    internal static partial void HandleInitialResourceData(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Handle resource changes.")]
    internal static partial void HandleResourceChanges(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Start log watching for the resource {resourceName}.")]
    internal static partial void StartLogWatchingForResource(this ILogger logger, string resourceName);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Stop log watching for the resource {resourceName}, lifetime is alive {isAlive}.")]
    internal static partial void StopLogWatchingForResource(this ILogger logger, string resourceName, bool isAlive);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Resource log watching was cancelled.")]
    internal static partial void ResourceLogWatchingWasCancelled(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Sending log watching request for the resource {resourceName}.")]
    internal static partial void SendingLogWatchingRequestForResource(this ILogger logger, string resourceName);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Resource log line received {logLine}.")]
    internal static partial void ResourceLogLineReceived(this ILogger logger, ConsoleLogLine? logLine);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Log watching request was cancelled.")]
    internal static partial void LogWatchingRequestWasCancelled(this ILogger logger);
}