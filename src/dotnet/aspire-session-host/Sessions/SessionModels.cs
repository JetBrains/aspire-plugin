using JetBrains.Annotations;

namespace AspireSessionHost.Sessions;

[UsedImplicitly]
internal sealed record Info(
    List<string> ProtocolsSupported
);

[UsedImplicitly]
internal sealed record Session(
    string ProjectPath,
    bool? Debug,
    string? LaunchProfile,
    bool? DisableLaunchProfile,
    EnvironmentVariable[]? Env,
    string[]? Args
);

[UsedImplicitly]
internal sealed record EnvironmentVariable(string Name, string? Value);