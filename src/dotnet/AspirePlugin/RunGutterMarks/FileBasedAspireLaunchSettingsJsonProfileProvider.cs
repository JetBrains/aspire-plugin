using JetBrains.Application.changes;
using JetBrains.Application.Parts;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DotNetCore;

namespace JetBrains.Rider.Aspire.Plugin.RunGutterMarks;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
internal sealed class FileBasedAspireLaunchSettingsJsonProfileProvider(
    Lifetime lifetime,
    FileBasedAspireLaunchSettingsJsonDataCache cache,
    ChangeManager changeManager,
    ISolution solution)
    : DotNetCoreLaunchSettingsJsonProfileProvider(lifetime, cache, changeManager, solution);
