using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using JetBrains.Rider.Aspire.Worker.Sessions;

namespace JetBrains.Rider.Aspire.Worker.IntegrationTests;

public class SessionEndpointTests(AspireWorkerWebApplicationFactory<Program> factory)
    : IClassFixture<AspireWorkerWebApplicationFactory<Program>>
{
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
    };

    [Fact]
    public async Task InfoEndpointReturnsCurrentProtocolVersion()
    {
        var client = factory.CreateClient();
        var response = await client.GetAsync("/info");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var info = await response.Content.ReadFromJsonAsync<Info>(_jsonOptions);
        Assert.NotNull(info);
        var version = Assert.Single(info.ProtocolsSupported);
        Assert.Equal("2024-03-03", version);
    }
}