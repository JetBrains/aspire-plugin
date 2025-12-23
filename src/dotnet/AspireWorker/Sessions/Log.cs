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

}