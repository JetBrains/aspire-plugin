using System.Text.Json;
using JetBrains.Rider.Aspire.Worker;
using JetBrains.Rider.Aspire.Worker.AspireHost;
using JetBrains.Rider.Aspire.Worker.Configuration;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Serilog;
using Serilog.Events;

Log.Logger = new LoggerConfiguration()
    .MinimumLevel.Override("Microsoft", LogEventLevel.Information)
    .Enrich.FromLogContext()
    .WriteTo.Console()
    .CreateBootstrapLogger();

try
{
    ParentProcessWatchdog.StartNewIfAvailable();

    var builder = WebApplication.CreateBuilder(args);

    builder.Configuration.AddEnvironmentVariables("RIDER_");
    builder.Services.ConfigureOptions<ConfigureDcpSessionOptions>();

    builder.Services.AddGrpc();

    var connection = new Connection(builder.Configuration);
    builder.Services.AddSingleton(connection);

    builder.Services.AddAspireHostServices();

    builder.Services.AddSerilog((services, lc) => lc
        .ReadFrom.Configuration(builder.Configuration)
        .ReadFrom.Services(services));

    builder.Services.ConfigureHttpJsonOptions(it =>
    {
        it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
    });

    var app = builder.Build();

    await app.Services.InitializeAspireHostServices();

    app.UseWebSockets();

    app.MapSessionEndpoints();

    app.Run();
}
catch (Exception ex)
{
    Log.Fatal(ex, "Application terminated unexpectedly");
}
finally
{
    Log.CloseAndFlush();
}