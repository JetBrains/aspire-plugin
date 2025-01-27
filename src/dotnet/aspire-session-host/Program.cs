using System.Text.Json;
using JetBrains.Rider.Aspire.SessionHost;
using JetBrains.Rider.Aspire.SessionHost.AspireHost;
using JetBrains.Rider.Aspire.SessionHost.Sessions;

ParentProcessWatchdog.StartNewIfAvailable();

var builder = WebApplication.CreateBuilder(args);

builder.Configuration.AddEnvironmentVariables("Rider_");

builder.Services.AddGrpc();

var connection = new Connection(builder.Configuration);
builder.Services.AddSingleton(connection);

builder.Services.AddAspireHostServices();

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

var app = builder.Build();

await app.Services.InitializeAspireHostServices();

app.UseWebSockets();

app.MapSessionEndpoints();

app.Run();