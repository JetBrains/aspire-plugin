using System.Collections.Generic;
using System.Linq;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.DotNetCore;
using JetBrains.ProjectModel.Properties;
using JetBrains.ReSharper.Features.Running;
using JetBrains.Rider.Model;
using JetBrains.Util;
using static AspirePlugin.Project.AspireHostProjectPropertyRequest;
using Key = JetBrains.Rider.Model.Key;

namespace AspirePlugin.RunnableProject;

[SolutionComponent]
public class AspireRunnableProjectProvider(
    ProjectRunnableOutputDetector projectRunnableOutputDetector,
    DotNetCoreLaunchSettingsJsonProfileProvider launchSettingsProvider,
    ILogger logger
) : IRunnableProjectProvider
{
    private const string DefaultDashboardUrl = "http://localhost:18888";
    private const string AspnetcoreUrlsEnvVar = "ASPNETCORE_URLS";

    public JetBrains.Rider.Model.RunnableProject? CreateRunnableProject(
        IProject project,
        string name,
        string fullName,
        IconModel? icon)
    {
        if (!project.IsDotNetCoreProject()) return null;

        var isAspireHost = project.GetUniqueRequestedProjectProperty(IsAspireHost);
        if (isAspireHost.IsNullOrEmpty() || isAspireHost != "true") return null;

        var projectOutputs = new List<ProjectOutput>();
        foreach (var tfm in project.TargetFrameworkIds)
        {
            var projectOutput = projectRunnableOutputDetector.CalculateProjectOutput(project, tfm);
            if (projectOutput == null)
            {
                logger.Trace($"Unable to find output for project for {tfm}");
                continue;
            }

            projectOutputs.Add(projectOutput);
        }

        var applicationUrl = DefaultDashboardUrl;
        var launchBrowser = false;
        var environmentVariables = new List<EnvironmentVariable>();

        var profileContent = GetProfileContent(project);
        if (profileContent is not null)
        {
            applicationUrl = profileContent.ApplicationUrl?.FirstOrDefault() ?? DefaultDashboardUrl;
            launchBrowser = profileContent.LaunchBrowser;

            environmentVariables.AddRange(
                profileContent
                    .EnvironmentVariables
                    .Select(env => new EnvironmentVariable(env.Key, env.Value))
                );

            if (!profileContent.EnvironmentVariables.ContainsKey(AspnetcoreUrlsEnvVar))
            {
                environmentVariables.Add(new EnvironmentVariable(AspnetcoreUrlsEnvVar, applicationUrl));
            }
        }

        var customAttributes = new List<CustomAttribute>
        {
            new(Key.StartBrowserUrl, applicationUrl),
            new(Key.LaunchBrowser, $"{launchBrowser}")
        };

        return new JetBrains.Rider.Model.RunnableProject(
            name,
            fullName,
            project.ProjectFileLocation.NormalizeSeparators(FileSystemPathEx.SeparatorStyle.Unix),
            AspireRunnableProjectKinds.AspireHost,
            projectOutputs,
            environmentVariables,
            null,
            customAttributes
        );
    }

    private LaunchSettingsJson.ProfileContent? GetProfileContent(IProject project)
    {
        var hasLaunchSettings = launchSettingsProvider.HasLaunchSettingsJson(project);
        if (!hasLaunchSettings) return null;

        var profiles = launchSettingsProvider.TryGetLaunchJsonSettingsProfiles(project);
        return profiles
            ?.Profiles
            ?.FirstOrDefault(it => it.CommandName == LaunchSettingsJson.ProjectCommand);
    }

    public IEnumerable<RunnableProjectKind> HiddenRunnableProjectKinds => EmptyList<RunnableProjectKind>.Instance;
}