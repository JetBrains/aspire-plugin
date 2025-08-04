using JetBrains.Application.Threading;
using JetBrains.ProjectModel;
using JetBrains.Util;

namespace JetBrains.Rider.Aspire.Plugin.Project;

public static class SolutionExtensions
{
    public static IProject? FindProjectByPath(this ISolution solution, VirtualFileSystemPath projectFilePath)
    {
        IProject? project;
        using (solution.Locks.UsingReadLock())
        {
            project = solution.FindProjectByProjectFilePath(projectFilePath);
        }

        return project;
    }
}