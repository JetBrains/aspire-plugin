using System.Text.Json;
using AspireSessionHost;
using AspireSessionHost.OTel;
using AspireSessionHost.Resources;
using AspireSessionHost.Sessions;

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

var app = builder.Build();

await app.Services.InitializeSessionServices();
await app.Services.InitializeResourceServices();
await app.Services.InitializeOTelServices();

app.UseWebSockets();

app.MapSessionEndpoints();
app.MapOTelEndpoints();

app.Run();