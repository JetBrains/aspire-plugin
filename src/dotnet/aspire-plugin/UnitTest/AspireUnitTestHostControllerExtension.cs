using System;
using System.Threading.Tasks;
using AspirePlugin.Generated;
using AspirePlugin.Project;
using JetBrains.Application.Parts;
using JetBrains.Core;
using JetBrains.ProjectModel;
using JetBrains.ProjectModel.Assemblies.Interfaces;
using JetBrains.ProjectModel.Properties;
using JetBrains.Rd.Tasks;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.UnitTestFramework.Execution.Hosting;
using JetBrains.ReSharper.UnitTestFramework.Execution.Launch;
using JetBrains.ReSharper.UnitTestFramework.Execution.TestRunner;
using JetBrains.Util;
using JetBrains.Util.Dotnet.TargetFrameworkIds;
// ReSharper disable ConvertIfStatementToReturnStatement

namespace AspirePlugin.UnitTest;

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
        System.Diagnostics.Debugger.Launch();
        if (run.RuntimeDescriptor is TestRunnerRuntimeDescriptor.NetCore netDescriptor)
        {
            var project = netDescriptor.Project;
            var aspireHostProject = GetAspireHostProject(project, netDescriptor.TargetFrameworkId);
            if (aspireHostProject is not null)
            {
                var isDebugging = run.Launch.HostProvider is DebugHostProvider;
                var sessionHostModel = new SessionHostModel(isDebugging);
                var model = solution.GetProtocolSolution().GetAspirePluginModel();
                var task = model.StartSessionHost.Start(run.Lifetime, sessionHostModel) as RdTask<Unit>;
                await task.AsTask();
            }
        }

        await next.PrepareForRun(run);
    }

    public async Task CleanupAfterRun(IUnitTestRun run, ITaskRunnerHostController next)
    {
        await next.CleanupAfterRun(run);
    }

    public void Cancel(IUnitTestRun run)
    {
    }

    private IProject? GetAspireHostProject(IProject projectUnderTest, TargetFrameworkId targetFrameworkId)
    {
        var referenced = projectUnderTest.GetReferencedProjects(targetFrameworkId);
        foreach (var referencedProject in referenced)
        {
            var property =
                referencedProject.GetUniqueRequestedProjectProperty(AspireHostProjectPropertyRequest.IsAspireHost);
            if (!property.IsNullOrEmpty() && string.Equals(property, "true", StringComparison.OrdinalIgnoreCase))
            {
                return referencedProject;
            }
        }

        return null;
    }
}