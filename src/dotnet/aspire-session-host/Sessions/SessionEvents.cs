using System.Text.Json.Serialization;

namespace AspireSessionHost.Sessions;

[JsonDerivedType(typeof(ProcessStartedEvent))]
[JsonDerivedType(typeof(LogReceivedEvent))]
[JsonDerivedType(typeof(ProcessTerminatedEvent))]
internal interface ISessionEvent;

internal sealed record ProcessStartedEvent(string SessionId, string NotificationType, long Pid)
    : ISessionEvent;

internal sealed record LogReceivedEvent(string SessionId, string NotificationType, bool IsStdErr, string LogMessage)
    : ISessionEvent;

internal sealed record ProcessTerminatedEvent(string SessionId, string NotificationType, int ExitCode)
    : ISessionEvent;