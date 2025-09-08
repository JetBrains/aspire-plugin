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
        Level = LogLevel.Warning,
        Message = "Only a single project launch configuration is supported.")]
    internal static partial void OnlySingleProjectLaunchConfigurationIsSupported(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Project file doesn't exist.")]
    internal static partial void ProjectFileDoesntExist(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Create a new session for {projectFilePath} request received.")]
    internal static partial void CreateNewSessionRequestReceived(this ILogger logger, string projectFilePath);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "A new session creation request built: {createSessionRequest}.")]
    internal static partial void SessionCreationRequestBuilt(this ILogger logger,
        CreateSessionRequest createSessionRequest);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "A session creation response received: {createSessionResponse}.")]
    internal static partial void SessionCreationResponseReceived(this ILogger logger,
        CreateSessionResponse? createSessionResponse);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Delete a session with {sessionId} request received.")]
    internal static partial void DeleteSessionRequestReceived(this ILogger logger, string sessionId);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "A new session deletion request built: {deleteSessionRequest}.")]
    internal static partial void SessionDeletionRequestBuilt(this ILogger logger,
        DeleteSessionRequest deleteSessionRequest);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "A session deletion response received: {deleteSessionResponse}.")]
    internal static partial void SessionDeletionResponseReceived(this ILogger logger,
        DeleteSessionResponse? deleteSessionResponse);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Process started {processStartedEvent}.")]
    internal static partial void ProcessStarted(this ILogger logger, ProcessStarted processStartedEvent);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Failed to write process started event.")]
    internal static partial void FailedToWriteProcessStartedEvent(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Log received {logReceivedEvent}.")]
    internal static partial void LogReceived(this ILogger logger, LogReceived logReceivedEvent);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Message is empty after processing.")]
    internal static partial void MessageIsEmptyAfterProcessing(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Failed to write log received event.")]
    internal static partial void FailedToWriteLogReceivedEvent(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Trace,
        Message = "Process terminated {processTerminatedEvent}.")]
    internal static partial void ProcessTerminated(this ILogger logger, ProcessTerminated processTerminatedEvent);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Failed to write process terminated event.")]
    internal static partial void FailedToWriteProcessTerminatedEvent(this ILogger logger);

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