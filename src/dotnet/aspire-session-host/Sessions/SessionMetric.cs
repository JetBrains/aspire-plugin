namespace AspireSessionHost.Sessions;

internal record SessionMetric(
    string ServiceName,
    string ScopeName,
    string MetricName,
    string? Description,
    string? Unit,
    double Value,
    long Timestamp
);