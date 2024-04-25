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

var builder = WebApplication.CreateBuilder(args);

builder.Configuration.AddEnvironmentVariables("Rider_");
builder.Services.ConfigureOptions<ConfigureResourceServiceOptions>();
builder.Services.ConfigureOptions<ConfigureOTelServiceOptions>();

builder.Services.AddGrpc();

var connection = new Connection(rdPort.Value);
builder.Services.AddSingleton(connection);

builder.Services.AddSessionServices();
builder.Services.AddResourceServices(builder.Configuration);
builder.Services.AddOTelServices(builder.Configuration);

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
await app.Services.InitializeOTelServices();

app.UseWebSockets();

app.MapSessionEndpoints();
app.MapOTelEndpoints();

app.Run();