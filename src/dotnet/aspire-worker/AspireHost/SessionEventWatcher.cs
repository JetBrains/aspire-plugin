using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.Sessions;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class SessionEventWatcher(
    RdConnection.RdConnection connection,
    AspireHostModel hostModel,
    ChannelWriter<ISessionEvent> sessionEventWriter,
    ILogger logger,
    Lifetime lifetime)
{
    internal async Task WatchSessionEvents()
    {
        await connection.DoWithModel(_ =>
        {
            hostModel.ProcessStarted.Advise(lifetime, it =>
            {
                logger.ProcessStarted(it);
                var writingResult =
                    sessionEventWriter.TryWrite(new ProcessStartedEvent(it.Id, "processRestarted", it.Pid));
                if (!writingResult)
                {
                    logger.FailedToWriteProcessStartedEvent();
                }
            });

            hostModel.LogReceived.Advise(lifetime, it =>
            {
                logger.LogReceived(it);
                var message = ModifyText(it.Message);
                if (string.IsNullOrWhiteSpace(message))
                {
                    logger.MessageIsEmptyAfterProcessing();
                    return;
                }

                var writingResult =
                    sessionEventWriter.TryWrite(new LogReceivedEvent(it.Id, "serviceLogs", it.IsStdErr, message));
                if (!writingResult)
                {
                    logger.FailedToWriteLogReceivedEvent();
                }
            });

            hostModel.ProcessTerminated.Advise(lifetime, it =>
            {
                logger.ProcessTerminated(it);
                var writingResult =
                    sessionEventWriter.TryWrite(new ProcessTerminatedEvent(it.Id, "sessionTerminated", it.ExitCode));
                if (!writingResult)
                {
                    logger.FailedToWriteProcessTerminatedEvent();
                }
            });
        });
    }

    private static string ModifyText(string text)
    {
        var modified = text;

        if (modified.StartsWith("\r\n"))
        {
            modified = modified[2..];
        }
        else if (modified.StartsWith('\n'))
        {
            modified = modified[1..];
        }

        if (modified.EndsWith("\r\n"))
        {
            modified = modified[..^2];
        }
        else if (modified.EndsWith('\n'))
        {
            modified = modified[..^1];
        }

        return modified;
    }
}