using System.Text.Json.Serialization;
using JetBrains.Annotations;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

[JsonDerivedType(typeof(ProcessStartedEvent))]
[JsonDerivedType(typeof(LogReceivedEvent))]
[JsonDerivedType(typeof(ProcessTerminatedEvent))]
internal interface ISessionEvent;

[PublicAPI]
internal sealed record ProcessStartedEvent(string SessionId, string NotificationType, long Pid)
    : ISessionEvent;

[PublicAPI]
internal sealed record LogReceivedEvent(string SessionId, string NotificationType, bool IsStdErr, string LogMessage)
    : ISessionEvent;

[PublicAPI]
internal sealed record ProcessTerminatedEvent(string SessionId, string NotificationType, int ExitCode)
    : ISessionEvent;