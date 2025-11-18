using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using AutoFixture;
using JetBrains.Rider.Aspire.Worker.Generated;
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
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var session = CreateAspireSession(fixture);
        var response = await client.PutAsJsonAsync("/run_session/?api-version=2024-03-03", session, _jsonOptions);

        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        var location = response.Headers.Location?.OriginalString;
        Assert.NotNull(location);
        const string runSessionPrefix = "/run_session/";
        Assert.StartsWith(runSessionPrefix, location);
        var sessionId = location[runSessionPrefix.Length..];
        var connectionWrapper = factory.Services.GetRequiredService<InMemoryConnectionWrapper>();
        var createRequests = connectionWrapper.GetCreateSessionRequestsById(sessionId);
        var request = Assert.Single(createRequests);
        var expectedLaunchConfig = session.LaunchConfigurations.Single();
        Assert.Equal(expectedLaunchConfig.ProjectPath, request.ProjectPath);
        Assert.Equal(expectedLaunchConfig.LaunchProfile, request.LaunchProfile);
        Assert.Equal(expectedLaunchConfig.DisableLaunchProfile, request.DisableLaunchProfile);
        Assert.False(request.Debug);
        Assert.Equal(session.Args, request.Args);
        Assert.Equal(
            session.Env?.Select(it => (it.Name, it.Value ?? "")),
            request.Envs?.Select(it => (it.Key, it.Value)));
    }

    [Fact]
    public async Task CreateSessionEndpointWithInvalidApiVersionReturns400()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var session = CreateAspireSession(fixture);
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
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "wrong-token");
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var session = CreateAspireSession(fixture);
        var response = await client.PutAsJsonAsync("/run_session/?api-version=2024-03-03", session, _jsonOptions);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task DeleteSessionEndpointReturns200AndDeleteSession()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var sessionId = fixture.Create<string>();
        var deleteResponse = await client.DeleteAsync($"/run_session/{sessionId}?api-version=2024-03-03");

        Assert.Equal(HttpStatusCode.OK, deleteResponse.StatusCode);
        var connectionWrapper = factory.Services.GetRequiredService<InMemoryConnectionWrapper>();
        var deleteRequests = connectionWrapper.GetDeletedSessionRequestsById(sessionId);
        var request = Assert.Single(deleteRequests);
        Assert.Equal(sessionId, request.SessionId);
    }

    [Fact]
    public async Task DeleteSessionEndpointWithInvalidApiVersionReturns400()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", AspireWorkerWebApplicationFactory<Program>.TestToken);
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var sessionId = fixture.Create<string>();
        var deleteResponse = await client.DeleteAsync($"/run_session/{sessionId}?api-version=wrong");

        Assert.Equal(HttpStatusCode.BadRequest, deleteResponse.StatusCode);
        var error = await deleteResponse.Content.ReadFromJsonAsync<ErrorResponse>(_jsonOptions);
        Assert.NotNull(error);
        Assert.Equal("ProtocolVersionIsNotSupported", error.Error.Code);
    }

    [Fact]
    public async Task DeleteSessionEndpointWithInvalidTokenReturns403()
    {
        var fixture = new Fixture();
        var dcpInstanceId = fixture.Create<string>();
        var aspireHostId = dcpInstanceId[..5];
        AddNewAspireHost(aspireHostId, fixture);

        var client = factory.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "wrong-token");
        client.DefaultRequestHeaders.Add("Microsoft-Developer-DCP-Instance-ID", dcpInstanceId);

        var sessionId = fixture.Create<string>();
        var deleteResponse = await client.DeleteAsync($"/run_session/{sessionId}?api-version=2024-03-03");

        Assert.Equal(HttpStatusCode.Unauthorized, deleteResponse.StatusCode);
    }

    private void AddNewAspireHost(string aspireHostId, Fixture fixture)
    {
        var config = new AspireHostModelConfig(
            aspireHostId,
            "AspireApp.AppHost: http",
            fixture.Create<string>(),
            null,
            null,
            null,
            null);
        var host = new AspireHostModel(config);

        var connectionWrapper = factory.Services.GetRequiredService<InMemoryConnectionWrapper>();
        connectionWrapper.AddHost(aspireHostId, host);
    }

    private static Session CreateAspireSession(Fixture fixture)
    {
        var launchConfiguration = new LaunchConfiguration(
            "project",
            fixture.Create<string>(),
            Mode.NoDebug,
            "http",
            false
        );

        var args = fixture.Create<string[]>();
        var envVariables = fixture.Create<EnvironmentVariable[]>();

        return new Session([launchConfiguration], envVariables, args);
    }
}