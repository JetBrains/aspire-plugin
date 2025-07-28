namespace JetBrains.Rider.Aspire.SessionHost.Configuration;

public class ConnectionOptions
{
    public const string SectionName = "CONNECTION";

    // ReSharper disable once PropertyCanBeMadeInitOnly.Global
    public int? RdPort { get; set; }
}