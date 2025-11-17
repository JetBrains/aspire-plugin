using System.Text.Json;
using JetBrains.Rider.Aspire.Worker;
using JetBrains.Rider.Aspire.Worker.AspireHost;
using JetBrains.Rider.Aspire.Worker.Authentication;
using JetBrains.Rider.Aspire.Worker.Configuration;
using JetBrains.Rider.Aspire.Worker.RdConnection;
using JetBrains.Rider.Aspire.Worker.Sessions;
using Microsoft.AspNetCore.Authentication;
using Serilog;
using Log = Serilog.Log;

Log.Logger = new LoggerConfiguration()
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

    builder.Services.AddRdConnectionServices(builder.Configuration);

    builder.Services.AddAspireHostServices();

    builder.Services.AddSerilog((services, lc) => lc
        .ReadFrom.Configuration(builder.Configuration)
        .ReadFrom.Services(services));

    builder.Services.ConfigureHttpJsonOptions(it =>
    {
        it.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
    });

    builder.Services
        .AddAuthentication("DcpToken")
        .AddScheme<AuthenticationSchemeOptions, DcpTokenAuthenticationHandler>("DcpToken", _ => { });

    builder.Services.AddAuthorization();

    var app = builder.Build();

    app.UseWebSockets();

    app.UseAuthentication();
    app.UseAuthorization();

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

public partial class Program;