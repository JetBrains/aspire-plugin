using JetBrains.Diagnostics;

namespace AspireSessionHost;

internal static class ParentProcessWatchdog
{
    private const string RiderParentProcessPid = "RIDER_PARENT_PROCESS_PID";

    internal static bool IsAvailable =>
        !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(RiderParentProcessPid));

    internal static void StartNew() =>
        ProcessWatchdog.StartWatchdogForPidEnvironmentVariable(RiderParentProcessPid);
}