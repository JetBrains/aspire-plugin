namespace JetBrains.Rider.Aspire.SessionHost;

public class ConnectionOptions
{
    public const string SectionName = "Connection";

    // ReSharper disable once PropertyCanBeMadeInitOnly.Global
    public int? RdPort { get; set; }
}