using JetBrains.Application.changes;
using JetBrains.Application.Parts;
using JetBrains.Lifetimes;
using JetBrains.ProjectModel;
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
internal sealed class AspireTemplateProvider(
    Lifetime lifetime,
    FileBasedAspireLaunchSettingsJsonDataCache cache,
    ChangeManager changeManager,
    ISolution solution
) : IRunConfigurationTemplateProvider
{
    private readonly DotNetCoreLaunchSettingsJsonProfileProvider _launchSettingsJsonProfileProvider =
        new(lifetime, cache, changeManager, solution);

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

        var csFilePath = (runMarkerHighlighting as FileBasedProgramRunMarkerHighlighting)?.FilePath;
        // Use the `.cs` path reported by the gutter mark as the app host file path; `aspire.config.json`
        // is only used to read the launch profiles, not the app host path.
        var launchSettingsFilePath = csFilePath?.Parent.Combine("aspire.config.json") 
                                     ?? project.Location / "Properties" / "launchSettings.json";
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
                            new(RunConfigurationTemplateKey.DotNetFilePath, csFilePath?.FullPath ?? ""),
                            new(RunConfigurationTemplateKey.ProjectFilePath, project.ProjectFileLocation.FullPath),
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