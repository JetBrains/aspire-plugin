using JetBrains.Application.changes;
using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Caches;
using JetBrains.ProjectModel.DotNetCore;
using JetBrains.ReSharper.Features.Running;
using JetBrains.Rider.Aspire.Plugin.RunnableProject;
using JetBrains.Rider.Backend.Features.RunMarkers;
using JetBrains.Rider.Backend.Features.RunMarkers.RunConfigurationTemplateProviders;
using JetBrains.Rider.Model;
using JetBrains.Util;
using JetBrains.Util.Dotnet.TargetFrameworkIds;

namespace JetBrains.Rider.Aspire.Plugin.RunGutterMarks;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
internal sealed class AspireTemplateProvider : IRunConfigurationTemplateProvider
{
    private readonly DotNetCoreLaunchSettingsJsonProfileProvider _launchSettingsJsonProfileProvider;

    public AspireTemplateProvider(
        Lifetime lifetime,
        FileBasedAspireLaunchSettingsJsonDataCache cache,
        ChangeManager changeManager,
        ISolution solution
    )
    {
        _launchSettingsJsonProfileProvider =
            new DotNetCoreLaunchSettingsJsonProfileProvider(lifetime, cache, changeManager, solution);
    }

    private const string AspireHostConfigurationTypeId = "AspireHostConfiguration";

    public IEnumerable<GeneratedRunConfigurationTemplate> CreateRunConfigurationTemplates(IProject project,
        IRunMarkerHighlighting runMarkerHighlighting,
        IEnumerable<RunConfigurationTemplate> runAndDebugTemplate,
        IEnumerable<Model.RunnableProject> runnableProjects, TargetFrameworkId tfmId,
        IEnumerable<RunConfiguration> existingRunConfigurations
    )
    {
        var aspireTemplates = runAndDebugTemplate.Where(x =>
            x.CompatibleRunnableProjectKinds.Contains(AspireRunnableProjectKinds.AspireHost.Name)).ToList();
        var aspireRunnableProjects =
            runnableProjects.Where(x => x.Kind == AspireRunnableProjectKinds.AspireHost).Where(runnableProject =>
                runnableProject.ProjectOutputs
                    .SingleItem(p => p.Tfm?.Equals(tfmId.ToRdTargetFrameworkInfo()) ?? false) != null).ToList();
        var aspireExistingRunConfigurations = existingRunConfigurations
            .OfType<RunConfigurationExt>()
            .Where(x => x.TypeId == AspireHostConfigurationTypeId)
            .Select(x => (executorID: x.Executor.Id, profileName: GetProfileNameFromRunConfiguration(x)))
            .ToHashSet();

        if (aspireTemplates.IsEmpty() || aspireRunnableProjects.IsEmpty()) return [];

        var launchSettingsFilePath = (runMarkerHighlighting as FileBasedProgramRunMarkerHighlighting)?.FilePath.Parent /
                                     "apphost.run.json";
        if (launchSettingsFilePath == null) return [];

        var profiles = _launchSettingsJsonProfileProvider.TryGetLaunchJsonSettingsProfiles(launchSettingsFilePath)
            .Profiles;
        return profiles.SelectMany(profile => aspireTemplates
            .Where(templateDescriptor =>
                !aspireExistingRunConfigurations.Contains((templateDescriptor.Executor.Id, profile.Name)))
            .SelectMany(templateDescriptor =>
                aspireRunnableProjects.Select(runnableProject =>
                {
                    var name = GenerateConfigurationName(runnableProject, profile.Name);
                    return new GeneratedRunConfigurationTemplate(new RunConfigurationTemplate(
                        templateDescriptor.Executor,
                        templateDescriptor.TypeId,
                        templateDescriptor.CompatibleRunnableProjectKinds,
                        [
                            new(RunConfigurationTemplateKey.Name, name),
                            new(RunConfigurationTemplateKey.ProjectFilePath,
                                runnableProject.ProjectFilePath),
                            new(RunConfigurationTemplateKey.ProjectKind, runnableProject.Kind.Name),
                            new(RunConfigurationTemplateKey.TargetFramework, tfmId?.PresentableString ?? string.Empty),
                            new(RunConfigurationTemplateKey.LaunchSettingsProfile, profile.Name)
                        ]), runnableProject, name);
                }))).ToArray();
    }

    private static string GenerateConfigurationName(Model.RunnableProject runnableProject, string profileName) =>
        runnableProject.Name == profileName ? profileName : $"{runnableProject.Name}: {profileName}";

    private static string? GetProfileNameFromRunConfiguration(RunConfigurationExt runConfiguration) => runConfiguration
        .AdditionalEntries.Where(x => x.Key == RunConfigurationEntryKey.LaunchSettingsProfile)
        .SingleOrFirstOrDefaultErr()?.Value;
}

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