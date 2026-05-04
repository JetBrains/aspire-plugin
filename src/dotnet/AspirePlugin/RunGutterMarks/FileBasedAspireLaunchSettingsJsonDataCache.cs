using JetBrains.Application.changes;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Caches;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.RunGutterMarks;

[SolutionInstanceComponent(Instantiation.DemandAnyThreadSafe)]
internal class FileBasedAspireLaunchSettingsJsonDataCache(
    Lifetime lifetime,
    ISolution solution,
    ISolutionCaches caches,
    ChangeManager changeManager,
    IShellLocks locks)
    : LaunchSettingsJsonDataCacheImpl(lifetime, solution, caches, changeManager, locks)
{
    protected override bool ShouldProcessChangedFile(IProjectFile changedFile, IProject project)
    {
        return changedFile.Name.Equals("apphost.run.json", FileSystemDefinition.PathStringComparison)
               && Equals(changedFile.ParentFolder, project);
    }
}