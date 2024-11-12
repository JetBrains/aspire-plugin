﻿using System.Collections.Generic;
using System.Text.Json.Serialization;
using JetBrains.Annotations;

namespace JetBrains.Rider.Aspire.SessionHost.Sessions;

[UsedImplicitly]
internal sealed record Info(
    List<string> ProtocolsSupported
);

[UsedImplicitly]
internal sealed record Session(
    LaunchConfiguration[] LaunchConfigurations,
    EnvironmentVariable[]? Env,
    string[]? Args
);

[UsedImplicitly]
internal sealed record LaunchConfiguration(
    string Type,
    string ProjectPath,
    Mode? Mode,
    string? LaunchProfile,
    bool? DisableLaunchProfile
);

[JsonConverter(typeof(JsonStringEnumConverter<Mode>))]
internal enum Mode
{
    Debug,
    NoDebug
}

[UsedImplicitly]
internal sealed record EnvironmentVariable(string Name, string? Value);

[UsedImplicitly]
internal sealed record ErrorResponse(ErrorDetail Error);

[UsedImplicitly]
internal sealed record ErrorDetail(
    string Code,
    string Message,
    ErrorDetail[]? Details = null
);