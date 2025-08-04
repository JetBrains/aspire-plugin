using JetBrains.Application.Parts;
using JetBrains.Application.Threading;
using JetBrains.Core;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Assemblies.Interfaces;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.UnitTestFramework.Execution.Hosting;
using JetBrains.ReSharper.UnitTestFramework.Execution.Launch;
using JetBrains.ReSharper.UnitTestFramework.Execution.TestRunner;
using JetBrains.Rider.Aspire.Plugin.Generated;
using JetBrains.Util.Dotnet.TargetFrameworkIds;

// ReSharper disable ConvertIfStatementToReturnStatement

namespace JetBrains.Rider.Aspire.Plugin.UnitTest;

[SolutionComponent(Instantiation.DemandAnyThreadSafe)]
public class AspireUnitTestHostControllerExtension(ISolution solution) : ITaskRunnerHostControllerExtension
{
    private readonly NugetId _aspireHostingTesting = new("Aspire.Hosting.Testing");

    public bool IsApplicable(IUnitTestRun run)
    {
        if (run.RuntimeDescriptor is not TestRunnerRuntimeDescriptor.NetCore netDescriptor) return false;

        var project = netDescriptor.Project;

        var testingPackage = project.GetPackagesReference(_aspireHostingTesting, netDescriptor.TargetFrameworkId);
        if (testingPackage is null) return false;

        var aspireHostProject = GetAspireHostProject(project, netDescriptor.TargetFrameworkId);
        if (aspireHostProject is null) return false;

        return true;
    }

    public ClientControllerInfo? GetClientControllerInfo(IUnitTestRun run, ITaskRunnerHostController next)
    {
        return next.GetClientControllerInfo(run);
    }

    public async Task PrepareForRun(IUnitTestRun run, ITaskRunnerHostController next)
    {
        if (run.RuntimeDescriptor is TestRunnerRuntimeDescriptor.NetCore netDescriptor)
        {
            var project = netDescriptor.Project;
            var aspireHostProject = GetAspireHostProject(project, netDescriptor.TargetFrameworkId);
            var aspireHostProjectPath = aspireHostProject?.ProjectFile?.Location.FullPath;
            if (aspireHostProjectPath is not null)
            {
                var isDebugging = run.Launch.HostProvider is DebugHostProvider;
                var request = new StartSessionHostRequest(
                    run.Id,
                    aspireHostProjectPath,
                    isDebugging
                );
                var model = solution.GetProtocolSolution().GetAspirePluginModel();
                if (model.StartSessionHost.Start(run.Lifetime, request) is RdTask<StartSessionHostResponse> task)
                {
                    var response = await task.AsTask();
                    var envVariables = run.Settings.TestRunner.EnvironmentVariables;
                    foreach (var hostEnvironmentVariable in response.EnvironmentVariables)
                    {
                        envVariables.Value[hostEnvironmentVariable.Key] = hostEnvironmentVariable.Value;
                    }
                }
            }
        }

        await next.PrepareForRun(run);
    }

    public async Task CleanupAfterRun(IUnitTestRun run, ITaskRunnerHostController next)
    {
        var model = solution.GetProtocolSolution().GetAspirePluginModel();
        var request = new StopSessionHostRequest(run.Id);
        if (model.StopSessionHost.Start(run.Lifetime, request) is RdTask<Unit> task)
        {
            await task.AsTask();
        }

        await next.CleanupAfterRun(run);
    }

    public void Cancel(IUnitTestRun run)
    {
        var model = solution.GetProtocolSolution().GetAspirePluginModel();
        model.UnitTestRunCancelled(run.Id);
    }

    private IProject? GetAspireHostProject(IProject projectUnderTest, TargetFrameworkId targetFrameworkId)
    {
        using (solution.Locks.UsingReadLock())
        {
            var referenced = projectUnderTest.GetReferencedProjects(targetFrameworkId);
            return referenced.FirstOrDefault(referencedProject => referencedProject.IsAspireHostProject());
        }
    }
}