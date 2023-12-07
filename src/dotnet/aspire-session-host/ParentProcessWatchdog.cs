using JetBrains.Diagnostics;

namespace AspireSessionHost;

internal static class ParentProcessWatchdog
{
    private const string RiderParentProcessPid = "RIDER_PARENT_PROCESS_PID";

    public static void StartNewIfAvailable()
    {
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(RiderParentProcessPid)))
        {
            return;
        }

        ProcessWatchdog.StartWatchdogForPidEnvironmentVariable(RiderParentProcessPid);
    }
}