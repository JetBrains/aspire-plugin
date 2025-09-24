using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using AutoFixture;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Microsoft.Extensions.DependencyInjection;

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

    [Fact]
    public async Task CreateSessionEndpointReturns201AndStoresSession()
    {
        var fixture = new Fixture();
        var session = fixture.Create<Session>();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.PutAsJsonAsync("/run_session/?api-version=2024-03-03", session, _jsonOptions);

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var location = response.Headers.Location?.OriginalString;
        Assert.NotNull(location);
        const string runSessionPrefix = "/run_session/";
        Assert.StartsWith(runSessionPrefix, location);
        var sessionId = location[runSessionPrefix.Length..];
        var hostService = factory.Services.GetRequiredService<InMemoryAspireHostService>();
        var storedSession = hostService.GetSession(sessionId);
        Assert.NotNull(storedSession);
        Assert.Equal(dcpInstanceId[..5], storedSession.Value.HostId);
        Assert.Equivalent(session, storedSession.Value.Session);
    }

    [Fact]
    public async Task CreateSessionEndpointWithInvalidApiVersionReturns400()
    {
        var fixture = new Fixture();
        var session = fixture.Create<Session>();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.PutAsJsonAsync("/run_session/?api-version=wrong", session, _jsonOptions);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var error = await response.Content.ReadFromJsonAsync<ErrorResponse>(_jsonOptions);
        Assert.NotNull(error);
        Assert.Equal("ProtocolVersionIsNotSupported", error.Error.Code);
    }

    [Fact]
    public async Task CreateSessionEndpointWithInvalidTokenReturns403()
    {
        var fixture = new Fixture();
        var session = fixture.Create<Session>();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "wrong-token");
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.PutAsJsonAsync("/run_session/?api-version=2024-03-03", session, _jsonOptions);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task DeleteSessionEndpointReturns200AndDeleteSession()
    {
        var fixture = new Fixture();
        var session = fixture.Create<Session>();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var createResponse = await client.PutAsJsonAsync("/run_session/?api-version=2024-03-03", session, _jsonOptions);
        var location = createResponse.Headers.Location?.OriginalString;
        Assert.NotNull(location);
        var sessionId = location["/run_session/".Length..];

        var deleteResponse = await client.DeleteAsync($"/run_session/{sessionId}?api-version=2024-03-03");
        Assert.Equal(HttpStatusCode.OK, deleteResponse.StatusCode);
        var hostService = factory.Services.GetRequiredService<InMemoryAspireHostService>();
        var storedSession = hostService.GetSession(sessionId);
        Assert.Null(storedSession);
    }

    [Fact]
    public async Task DeleteSessionEndpointWithInvalidApiVersionReturns400()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.DeleteAsync("/run_session/some-id?api-version=wrong");
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var error = await response.Content.ReadFromJsonAsync<ErrorResponse>(_jsonOptions);
        Assert.NotNull(error);
        Assert.Equal("ProtocolVersionIsNotSupported", error.Error.Code);
    }

    [Fact]
    public async Task DeleteSessionEndpointWithInvalidTokenReturns403()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "wrong-token");
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.DeleteAsync("/run_session/some-id?api-version=2024-03-03");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task DeleteSessionEndpointWithUnknownSessionReturns400()
    {
        var fixture = new Fixture();
        var sessionId = fixture.Create<string>();
        var dcpInstanceId = fixture.Create<string>();

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var response = await client.DeleteAsync($"/run_session/{sessionId}?api-version=2024-03-03");
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        var error = await response.Content.ReadFromJsonAsync<ErrorResponse>(_jsonOptions);
        Assert.NotNull(error);
        Assert.Equal("AspireHostIsNotFound", error.Error.Code);
    }
}