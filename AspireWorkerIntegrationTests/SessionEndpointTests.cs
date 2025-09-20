using System.Net;
using System.Net.Http.Json;
using System.Threading.Tasks;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Microsoft.Extensions.DependencyInjection;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class SessionEndpointTests(AspireWorkerWebApplicationFactory<Program> factory)
    : IClassFixture<AspireWorkerWebApplicationFactory<Program>>
{
    [Fact]
    public async Task InfoEndpointReturnsCurrentProtocolVersion()
    {
        var client = factory.CreateClient();
        var response = await client.GetAsync("/info");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var info = await response.Content.ReadFromJsonAsync<Info>();
        Assert.NotNull(info);
        var version = Assert.Single(info.ProtocolsSupported);
        Assert.Equal("2024-03-03", version);
    }

    [Fact]
    public async Task CreateSessionRequestReturnSuccess()
    {
        var client = factory.CreateDefaultClient();

        var aspireHostService = factory.Services.GetService<InMemoryAspireHostService>();
    }
}