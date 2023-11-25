using JetBrains.Annotations;

namespace AspireSessionHost;

[UsedImplicitly]
internal record Session(
    string ProjectPath,
    bool Debug,
    EnvironmentVariable[]? Env,
    string[]? Args
);

[UsedImplicitly]
internal record EnvironmentVariable(string Name, string Value);