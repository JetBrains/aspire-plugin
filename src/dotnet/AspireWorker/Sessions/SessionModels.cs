using System.Text.Json.Serialization;
using JetBrains.Annotations;

namespace JetBrains.Rider.Aspire.Worker.Sessions;

[PublicAPI]
internal sealed record Info(
    List<string> ProtocolsSupported,
    List<string>? SupportedLaunchConfigurations = null
);

[PublicAPI]
internal sealed record Session(
    LaunchConfiguration[] LaunchConfigurations,
    EnvironmentVariable[]? Env,
    string[]? Args
);

[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(ProjectLaunchConfiguration), "project")]
[JsonDerivedType(typeof(PythonLaunchConfiguration), "python")]
[PublicAPI]
internal abstract record LaunchConfiguration;

[PublicAPI]
internal sealed record ProjectLaunchConfiguration(
    string ProjectPath,
    Mode? Mode,
    string? LaunchProfile,
    bool? DisableLaunchProfile
) : LaunchConfiguration;

[PublicAPI]
internal sealed record PythonLaunchConfiguration(
    string ProgramPath,
    Mode? Mode,
    string? InterpreterPath,
    string? Module
) : LaunchConfiguration;

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
internal record ErrorResponse(ErrorDetail Error);

[PublicAPI]
internal sealed record ErrorDetail(
    string Code,
    string Message,
    ErrorDetail[]? Details = null
);