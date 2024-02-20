using System.Globalization;
using System.Text.Json;
using AspireSessionHost;
using AspireSessionHost.OpenTelemetry;
using AspireSessionHost.Resources;
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

var otlpServerPortValue = Environment.GetEnvironmentVariable("RIDER_OTLP_SERVER_PORT");
int.TryParse(otlpServerPortValue, CultureInfo.InvariantCulture, out var otlpServerPort);

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddGrpc();

var connection = new Connection(rdPort);
builder.Services.AddSingleton(connection);

builder.Services.AddSessionServices();
builder.Services.AddResourceServices();
builder.Services.AddOTelServices();

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

builder.WebHost.ConfigureKestrel(it =>
{
    it.ListenLocalhost(aspNetCoreUrl.Port);
    if (otlpServerPort != 0)
    {
        it.ListenLocalhost(otlpServerPort, options =>
        {
            options.Protocols = HttpProtocols.Http2;
            options.UseHttps();
        });
    }
});

var app = builder.Build();

await app.Services.InitializeSessionServices();
app.Services.InitializeResourceServices();
await app.Services.InitializeOTelServices();

app.UseWebSockets();

app.MapSessionEndpoints();
app.MapOTelEndpoints();

app.Run();