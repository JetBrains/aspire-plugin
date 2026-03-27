using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.AspireHost;

internal interface IAspireHostService
{
    void AddNewHost(string id, AspireHostModel host, Lifetime lifetime);
    AspireHost? GetHost(string id);
}