using System.Threading.Tasks;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class BasicTests(AspireWorkerWebApplicationFactory<Program> factory)
    : IClassFixture<AspireWorkerWebApplicationFactory<Program>>
{
    [Fact]
    public async Task Test()
    {
        var client = factory.CreateDefaultClient();
    }
}