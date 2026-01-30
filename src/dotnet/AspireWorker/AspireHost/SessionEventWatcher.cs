using System.Threading.Channels;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using JetBrains.Rider.Aspire.Worker.Sessions;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal sealed class SessionEventWatcher(
    IRdConnectionWrapper connectionWrapper,
    AspireHostModel hostModel,
    ChannelWriter<ISessionEvent> sessionEventWriter,
    ILogger logger,
    Lifetime lifetime)
{
    //docs: https://github.com/dotnet/aspire/blob/main/docs/specs/IDE-execution.md#common-notification-properties
    private const string ProcessStartedEventName = "processRestarted";
    private const string ProcessTerminatedEventName = "sessionTerminated";
    private const string LogReceivedEventName = "serviceLogs";
    private const string MessageReceivedEventName = "sessionMessage";

    internal async Task WatchSessionEvents()
    {
        await connectionWrapper.AdviceOnProcessStarted(hostModel, lifetime,
            it => { ProcessStarted(it, sessionEventWriter, logger); });

        await connectionWrapper.AdviceOnProcessTerminated(hostModel, lifetime,
            it => { ProcessTerminated(it, sessionEventWriter, logger); });

        await connectionWrapper.AdviceOnLogReceived(hostModel, lifetime,
            it => { LogReceived(it, sessionEventWriter, logger); });

        await connectionWrapper.AdviceOnMessageReceived(hostModel, lifetime,
            it => { MessageReceived(it, sessionEventWriter, logger); });
    }

    private static void ProcessStarted(
        ProcessStarted @event,
        ChannelWriter<ISessionEvent> sessionEventWriter,
        ILogger logger)
    {
        logger.ProcessStarted(@event);

        var sessionEvent = new ProcessStartedEvent(@event.Id, ProcessStartedEventName, @event.Pid);
        var writingResult = sessionEventWriter.TryWrite(sessionEvent);
        if (!writingResult)
        {
            logger.FailedToWriteProcessStartedEvent();
        }
    }

    private static void ProcessTerminated(
        ProcessTerminated @event,
        ChannelWriter<ISessionEvent> sessionEventWriter,
        ILogger logger)
    {
        logger.ProcessTerminated(@event);

        var sessionEvent = new ProcessTerminatedEvent(@event.Id, ProcessTerminatedEventName, @event.ExitCode);
        var writingResult = sessionEventWriter.TryWrite(sessionEvent);
        if (!writingResult)
        {
            logger.FailedToWriteProcessTerminatedEvent();
        }
    }

    private static void LogReceived(
        LogReceived @event,
        ChannelWriter<ISessionEvent> sessionEventWriter,
        ILogger logger)
    {
        logger.LogReceived(@event);

        var message = ModifyText(@event.Message);
        if (string.IsNullOrWhiteSpace(message))
        {
            logger.MessageIsEmptyAfterProcessing();
            return;
        }

        var sessionEvent = new LogReceivedEvent(
            @event.Id,
            LogReceivedEventName,
            @event.IsStdErr,
            message);
        var writingResult = sessionEventWriter.TryWrite(sessionEvent);
        if (!writingResult)
        {
            logger.FailedToWriteLogReceivedEvent();
        }
    }

    private static void MessageReceived(
        MessageReceived @event,
        ChannelWriter<ISessionEvent> sessionEventWriter,
        ILogger logger)
    {
        logger.MessageReceived(@event);


        var level = @event.Level switch
        {
            MessageLevel.Error => "error",
            MessageLevel.Info => "info",
            MessageLevel.Debug => "debug",
            _ => "info"
        };
        var error = @event.Error?.ToError();
        var code = error?.ErrorDetail.Code;
        var details = error != null ? (ErrorDetail[])[error.ErrorDetail] : null;

        var sessionEvent = new MessageReceivedEvent(
            @event.Id,
            MessageReceivedEventName,
            level,
            @event.Message,
            code,
            details);
        var writingResult = sessionEventWriter.TryWrite(sessionEvent);
        if (!writingResult)
        {
            logger.FailedToWriteMessageReceivedEvent();
        }
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