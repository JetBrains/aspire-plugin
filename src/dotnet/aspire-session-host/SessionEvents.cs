using System.Text.Json.Serialization;

namespace AspireSessionHost;

[JsonDerivedType(typeof(ProcessStartedEvent))]
[JsonDerivedType(typeof(LogReceivedEvent))]
[JsonDerivedType(typeof(ProcessTerminatedEvent))]
internal interface ISessionEvent;

internal record ProcessStartedEvent(string SessionId, string NotificationType, long Pid) : ISessionEvent;

internal record LogReceivedEvent(string SessionId, string NotificationType, bool IsStdErr, string LogMessage) : ISessionEvent;

internal record ProcessTerminatedEvent(string SessionId, string NotificationType, int ExitCode) : ISessionEvent;