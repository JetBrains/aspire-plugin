namespace JetBrains.Rider.Aspire.Worker.Sessions;

internal static partial class Log
{
    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Create a new session request received.")]
    internal static partial void CreateNewSessionRequestReceived(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Delete a session with {sessionId} request received.")]
    internal static partial void DeleteSessionRequestReceived(this ILogger logger, string sessionId);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Notify request received.")]
    internal static partial void NotifyRequestReceived(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Information,
        Message = "Notify connection closed.")]
    internal static partial void NotifyConnectionClosed(this ILogger logger);

    [LoggerMessage(
        Level = LogLevel.Error,
        Message = "Notify connection error: {message}.")]
    internal static partial void NotifyConnectionError(this ILogger logger, string message);
}