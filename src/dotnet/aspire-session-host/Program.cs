using System.Text.Json;
using AspireSessionHost;
using AspireSessionHost.OTel;
using AspireSessionHost.Resources;
using AspireSessionHost.Sessions;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using static AspireSessionHost.EnvironmentVariables;

var aspNetCoreUrl = GetAspNetCoreUrls();
if (aspNetCoreUrl is null) throw new ApplicationException($"Unable to find {AspNetCoreUrls} variable");

ParentProcessWatchdog.StartNewIfAvailable();

var builder = WebApplication.CreateBuilder(args);

builder.Configuration.AddEnvironmentVariables("Rider_");
builder.Services.ConfigureOptions<ConfigureResourceServiceOptions>();
builder.Services.ConfigureOptions<ConfigureOTelServiceOptions>();

builder.Services.AddGrpc();

var connection = new Connection(builder.Configuration);
builder.Services.AddSingleton(connection);

builder.Services.AddSessionServices();
builder.Services.AddResourceServices(builder.Configuration);
builder.Services.AddOTelServices(builder.Configuration);

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

var otelServiceOptions =
    builder.Configuration.GetSection(ConfigureOTelServiceOptions.SectionName).Get<OTelServiceOptions>();
var otlpServerPort = otelServiceOptions?.ServerPort;
builder.WebHost.ConfigureKestrel(it =>
{
    it.ListenLocalhost(aspNetCoreUrl.Port);
    if (otlpServerPort != null && otlpServerPort != 0)
    {
        it.ListenLocalhost(otlpServerPort.Value, options => { options.Protocols = HttpProtocols.Http2; });
    }
});

var app = builder.Build();

await app.Services.InitializeSessionServices();
await app.Services.InitializeResourceServices();
await app.Services.InitializeOTelServices();

app.UseWebSockets();

app.MapSessionEndpoints();
app.MapOTelEndpoints();

app.Run();