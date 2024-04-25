using JetBrains.Diagnostics;

namespace AspireSessionHost;

internal static class ParentProcessWatchdog
{
    private const string RiderParentProcessProcessId = "RIDER_PARENT_PROCESS_ID";

    public static void StartNewIfAvailable()
    {
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(RiderParentProcessProcessId)))
        {
            return;
        }

        ProcessWatchdog.StartWatchdogForPidEnvironmentVariable(RiderParentProcessProcessId);
    }
}