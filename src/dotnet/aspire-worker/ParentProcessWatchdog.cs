using JetBrains.Diagnostics;

namespace JetBrains.Rider.Aspire.Worker;

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