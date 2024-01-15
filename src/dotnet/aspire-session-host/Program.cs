using System.Globalization;
using System.Text.Json;
using AspireSessionHost;
using AspireSessionHost.Otel;
using AspireSessionHost.Sessions;
using Microsoft.AspNetCore.Server.Kestrel.Core;

ParentProcessWatchdog.StartNewIfAvailable();

var aspNetCoreUrlValue = Environment.GetEnvironmentVariable("ASPNETCORE_URLS");
if (aspNetCoreUrlValue == null) throw new ApplicationException("Unable to find ASPNETCORE_URLS variable");
if (!Uri.TryCreate(aspNetCoreUrlValue, UriKind.Absolute, out var aspNetCoreUrl))
    throw new ApplicationException("ASPNETCORE_URLS is not a valid URI");

var rdPortValue = Environment.GetEnvironmentVariable("RIDER_RD_PORT");
if (rdPortValue == null) throw new ApplicationException("Unable to find RIDER_RD_PORT variable");
if (!int.TryParse(rdPortValue, CultureInfo.InvariantCulture, out var rdPort))
    throw new ApplicationException("RIDER_RD_PORT is not a valid port");

var otelPortValue = Environment.GetEnvironmentVariable("RIDER_OTEL_PORT");
if (otelPortValue == null) throw new ApplicationException("Unable to find RIDER_OTEL_PORT variable");
if (!int.TryParse(otelPortValue, CultureInfo.InvariantCulture, out var otelPort))
    throw new ApplicationException("RIDER_OTEL_PORT is not a valid port");

var otlpEndpointUrlValue = Environment.GetEnvironmentVariable("DOTNET_OTLP_ENDPOINT_URL");
Uri? otlpEndpointUrl = null;
if (otlpEndpointUrlValue != null) Uri.TryCreate(otlpEndpointUrlValue, UriKind.Absolute, out otlpEndpointUrl);

var connection = new Connection(rdPort);

var sessionEventService = new SessionEventService(connection);
await sessionEventService.Subscribe();

var sessionMetricService = new SessionMetricService(connection);
await sessionMetricService.Subscribe();

var sessionNodeService = new SessionNodeService(connection);
await sessionNodeService.Subscribe();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddGrpc();
if (otlpEndpointUrl != null) builder.Services.AddOtelClients(otlpEndpointUrl);
builder.Services.AddSingleton(connection);
builder.Services.AddSingleton(sessionEventService);
builder.Services.AddSingleton(sessionMetricService);
builder.Services.AddSingleton(sessionNodeService);
builder.Services.AddSingleton<SessionService>();

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

builder.WebHost.ConfigureKestrel(it =>
{
    it.ListenLocalhost(aspNetCoreUrl.Port);
    it.ListenLocalhost(otelPort, options =>
    {
        options.Protocols = HttpProtocols.Http2;
        options.UseHttps();
    });
});

var app = builder.Build();

app.UseWebSockets();

app.MapSessionEndpoints();
app.MapOtelEndpoints();

app.Run();