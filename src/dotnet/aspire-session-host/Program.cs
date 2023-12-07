using System.Globalization;
using System.Text.Json;
using AspireSessionHost;

ParentProcessWatchdog.StartNewIfAvailable();

var port = Environment.GetEnvironmentVariable("RIDER_RD_PORT");
if (port == null) throw new ApplicationException("Unable to find RIDER_RD_PORT variable");

var connection = new Connection(int.Parse(port, CultureInfo.InvariantCulture));
var sessionEventService = new SessionEventService();
await sessionEventService.Subscribe(connection);

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton(connection);
builder.Services.AddSingleton(sessionEventService);
builder.Services.AddSingleton<SessionService>();

builder.Services.ConfigureHttpJsonOptions(it =>
{
    it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

var app = builder.Build();

app.UseWebSockets();

app.MapSessionEndpoints();

app.Run();
