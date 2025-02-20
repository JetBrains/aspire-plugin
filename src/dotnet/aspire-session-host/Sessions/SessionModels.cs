using System.Text.Json.Serialization;
using JetBrains.Annotations;

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

[PublicAPI]
internal sealed record Info(
    List<string> ProtocolsSupported
);

[PublicAPI]
internal sealed record Session(
    LaunchConfiguration[] LaunchConfigurations,
    EnvironmentVariable[]? Env,
    string[]? Args
);

[PublicAPI]
internal sealed record LaunchConfiguration(
    string Type,
    string ProjectPath,
    Mode? Mode,
    string? LaunchProfile,
    bool? DisableLaunchProfile
);

[JsonConverter(typeof(JsonStringEnumConverter<Mode>))]
[PublicAPI]
internal enum Mode
{
    Debug,
    NoDebug
}

[PublicAPI]
internal sealed record EnvironmentVariable(string Name, string? Value);

[PublicAPI]
internal sealed record ErrorResponse(ErrorDetail Error);

[PublicAPI]
internal sealed record ErrorDetail(
    string Code,
    string Message,
    ErrorDetail[]? Details = null
);