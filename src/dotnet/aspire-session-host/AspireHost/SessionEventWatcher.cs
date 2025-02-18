using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.SessionHost.Generated;
using JetBrains.Rider.Aspire.SessionHost.Sessions;

namespace JetBrains.Rider.Aspire.SessionHost.AspireHost;

internal sealed class SessionEventWatcher(
    Connection connection,
    AspireHostModel hostModel,
    ChannelWriter<ISessionEvent> sessionEventWriter,
    ILogger logger,
    Lifetime lifetime)
{
    private static readonly string[] LineSeparators = ["\r", "\n", "\r\n"];

    internal async Task WatchSessionEvents()
    {
        await connection.DoWithModel(_ =>
        {
            hostModel.ProcessStarted.Advise(lifetime, it =>
            {
                logger.LogTrace("Process started {startedProcess}", it);
                var writingResult =
                    sessionEventWriter.TryWrite(new ProcessStartedEvent(it.Id, "processRestarted", it.Pid));
                if (!writingResult)
                {
                    logger.LogWarning("Failed to write process started event");
                }
            });

            hostModel.LogReceived.Advise(lifetime, it =>
            {
                logger.LogTrace("Log received {log}", it);
                var message = ModifyText(it.Message);
                if (string.IsNullOrEmpty(message))
                {
                    logger.LogTrace("Message is empty after processing");
                    return;
                }
                var writingResult =
                    sessionEventWriter.TryWrite(new LogReceivedEvent(it.Id, "serviceLogs", it.IsStdErr, message));
                if (!writingResult)
                {
                    logger.LogWarning("Failed to write log received event");
                }
            });

            hostModel.ProcessTerminated.Advise(lifetime, it =>
            {
                logger.LogTrace("Process terminated {terminatedProcess}", it);
                var writingResult =
                    sessionEventWriter.TryWrite(new ProcessTerminatedEvent(it.Id, "sessionTerminated", it.ExitCode));
                if (!writingResult)
                {
                    logger.LogWarning("Failed to write process terminated event");
                }
            });
        });
    }

    private static string ModifyText(string text)
    {
        var modified = string.Join("\r\n", text.Split(LineSeparators, StringSplitOptions.RemoveEmptyEntries));
        return modified;
    }
}