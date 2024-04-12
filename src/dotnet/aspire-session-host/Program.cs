using System.Text.Json;
using AspireSessionHost;
using AspireSessionHost.OTel;
using AspireSessionHost.Resources;
using AspireSessionHost.Sessions;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using static AspireSessionHost.EnvironmentVariables;

ParentProcessWatchdog.StartNewIfAvailable();

var aspNetCoreUrl = GetAspNetCoreUrls();
if (aspNetCoreUrl is null) throw new ApplicationException($"Unable to find {AspNetCoreUrls} variable");

var rdPort = GetRdPort();
if(!rdPort.HasValue) throw new ApplicationException($"Unable to find {RdPort} variable");

var otlpServerPort = GetOtlpServerPort();
var otlpEndpointUrl = GetOtlpEndpointUrl();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddGrpc();

var connection = new Connection(rdPort.Value);
builder.Services.AddSingleton(connection);

builder.Services.AddSessionServices();
builder.Services.AddResourceServices();
if (otlpEndpointUrl != null)
{
    builder.Services.AddOTelServices(otlpEndpointUrl);
}

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

builder.WebHost.ConfigureKestrel(it =>
{
    it.ListenLocalhost(aspNetCoreUrl.Port);
    if (otlpServerPort.HasValue && otlpServerPort != 0)
    {
        it.ListenLocalhost(otlpServerPort.Value, options =>
        {
            options.Protocols = HttpProtocols.Http2;
        });
    }
});

var app = builder.Build();

await app.Services.InitializeSessionServices();
await app.Services.InitializeResourceServices();
if (otlpEndpointUrl != null)
{
    await app.Services.InitializeOTelServices();
}

app.UseWebSockets();

app.MapSessionEndpoints();
if (otlpEndpointUrl != null)
{
    app.MapOTelEndpoints();
}

app.Run();