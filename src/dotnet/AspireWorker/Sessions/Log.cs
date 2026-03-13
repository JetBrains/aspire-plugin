using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal static partial class Log
{
    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Create a new session request received.")]
    internal static partial void CreateNewSessionHttpRequestReceived(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Delete a session with {sessionId} request received.")]
    internal static partial void DeleteSessionHttpRequestReceived(this ILogger logger, string sessionId);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Notify request received.")]
    internal static partial void NotifyRequestReceived(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Unable to find any supported launch configuration.")]
    internal static partial void UnableToFindAnySupportedLaunchConfiguration(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Create a new session for {filePath} request received.")]
    internal static partial void CreateNewSessionRequestReceived(this ILogger logger, string filePath);

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
        Message = "Process terminated {processTerminatedEvent}.")]
    internal static partial void ProcessTerminated(this ILogger logger, ProcessTerminated processTerminatedEvent);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Failed to write process terminated event.")]
    internal static partial void FailedToWriteProcessTerminatedEvent(this ILogger logger);

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
        Message = "Message received {messageReceivedEvent}.")]
    internal static partial void MessageReceived(this ILogger logger, MessageReceived messageReceivedEvent);

    [LoggerMessage(
        Level = LogLevel.Warning,
        Message = "Failed to write message received event.")]
    internal static partial void FailedToWriteMessageReceivedEvent(this ILogger logger);
    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Notify connection closed.")]
    internal static partial void NotifyConnectionClosed(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Error,
        Message = "Notify connection error: {message}.")]
    internal static partial void NotifyConnectionError(this ILogger logger, string message);
}